<template>
  <div class="chat-page">
    <div class="chat-shell">
      <div class="chat-header">
        <div class="header-main">
          <h2>Campus AI Chat</h2>
          <p>当前默认接入带 RAG、会话记忆与 Function Calling 的流式聊天接口。</p>
        </div>
        <div class="header-side">
          <el-tag size="small" type="success">SSE</el-tag>
          <el-tag size="small" type="info">conversation: {{ shortConversationId }}</el-tag>
          <el-button size="mini" plain @click="resetConversation">新会话</el-button>
        </div>
      </div>

      <div ref="messageListRef" class="message-list">
        <div
          v-for="(message, index) in messages"
          :key="index"
          :class="['message-row', message.role === 'user' ? 'is-user' : 'is-assistant']"
        >
          <div class="avatar">{{ message.role === 'user' ? 'U' : 'AI' }}</div>
          <div class="bubble">{{ message.content }}</div>
        </div>
      </div>

      <div class="composer">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="4"
          resize="none"
          placeholder="请输入问题，系统会自动携带 conversationId 进行多轮上下文对话。"
          @keyup.ctrl.enter.native="sendMessage"
        />
        <div class="composer-actions">
          <span class="tip">Ctrl + Enter 发送，当前会话会保存在浏览器本地</span>
          <el-button v-if="streaming" plain @click="stopCurrentStream">停止生成</el-button>
          <el-button type="primary" :loading="sending" @click="sendMessage">发送</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { getToken } from '@/utils/auth'

const STORAGE_KEY = 'campus_ai_conversation_id'
const PUBLIC_STREAM_ENDPOINT = '/api/ai/chat/public/stream'
const STUDENT_STREAM_ENDPOINT = '/api/ai/chat/student/stream'

export default {
  name: 'AiChat',
  data() {
    return {
      inputText: '',
      sending: false,
      streaming: false,
      conversationId: '',
      currentController: null,
      currentRequestId: 0,
      messages: [
        {
          role: 'assistant',
          content: '你好，我已经接入流式对话、会话记忆、知识库检索和工具调用。你可以直接开始提问。'
        }
      ]
    }
  },
  computed: {
    shortConversationId() {
      return this.conversationId ? this.conversationId.slice(0, 12) : 'N/A'
    },
    isStudentRole() {
      const roles = this.$store.getters.roles || []
      return roles.includes('student')
    }
  },
  created() {
    this.conversationId = this.loadConversationId()
  },
  beforeDestroy() {
    this.abortCurrentRequest('component destroy')
  },
  beforeRouteLeave(to, from, next) {
    this.abortCurrentRequest('route leave')
    next()
  },
  methods: {
    loadConversationId() {
      const cached = window.localStorage.getItem(STORAGE_KEY)
      if (cached) {
        return cached
      }
      const nextId = this.generateConversationId()
      window.localStorage.setItem(STORAGE_KEY, nextId)
      return nextId
    },
    generateConversationId() {
      if (window.crypto && window.crypto.randomUUID) {
        return window.crypto.randomUUID()
      }
      return `conv-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
    },
    resetConversation() {
      this.abortCurrentRequest('reset conversation')
      this.conversationId = this.generateConversationId()
      window.localStorage.setItem(STORAGE_KEY, this.conversationId)
      this.messages = [
        {
          role: 'assistant',
          content: '已创建新会话。后续提问将不再携带之前的上下文。'
        }
      ]
      this.$message.success('已切换到新会话')
    },
    async sendMessage() {
      const prompt = this.inputText.trim()
      if (!prompt) {
        return
      }

      if (this.hasActiveRequest()) {
        this.abortCurrentRequest('superseded by new message')
      }

      this.messages.push({ role: 'user', content: prompt })
      const assistantMessage = { role: 'assistant', content: '' }
      this.messages.push(assistantMessage)
      this.inputText = ''
      const requestId = this.currentRequestId + 1
      const controller = new AbortController()
      this.currentRequestId = requestId
      this.currentController = controller
      this.sending = true
      this.streaming = true
      this.scrollToBottom()

      try {
        const response = await this.requestStream(prompt, controller)

        if (!response.ok) {
          throw new Error(await this.extractErrorMessage(response, `Request failed with status ${response.status}`))
        }

        if (!response.body) {
          throw new Error('流式响应不可用，请稍后再试')
        }

        const contentType = (response.headers.get('content-type') || '').toLowerCase()
        if (!contentType.includes('text/event-stream')) {
          throw new Error(await this.extractErrorMessage(response, '大模型额度已耗尽，请稍后再试'))
        }

        await this.consumeStream(response, assistantMessage, requestId)
      } catch (error) {
        if (this.isAbortError(error)) {
          if (!assistantMessage.content) {
            assistantMessage.content = '已停止生成'
          }
          return
        }
        const message = error.message || '对话失败，请稍后再试'
        assistantMessage.content = message
        this.$message.error(message)
      } finally {
        if (this.currentRequestId === requestId) {
          this.finishCurrentRequest(controller)
        }
        this.scrollToBottom()
      }
    },
    async requestStream(prompt, controller) {
      const payload = {
        prompt,
        query: prompt,
        conversationId: this.conversationId
      }

      const endpoint = this.isStudentRole ? STUDENT_STREAM_ENDPOINT : PUBLIC_STREAM_ENDPOINT
      const url = `${process.env.VUE_APP_BASE_API}${endpoint}?conversationId=${encodeURIComponent(this.conversationId)}`
      return fetch(url, {
        method: 'POST',
        headers: this.buildHeaders(),
        body: JSON.stringify(payload),
        signal: controller.signal
      })
    },
    async consumeStream(response, assistantMessage, requestId) {
      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      try {
        while (this.isRequestActive(requestId)) {
          const { done, value } = await reader.read()
          if (done) {
            break
          }
          if (!this.isRequestActive(requestId)) {
            break
          }

          buffer += decoder.decode(value, { stream: true })
          const chunks = buffer.split(/\r?\n\r?\n/)
          buffer = chunks.pop() || ''

          chunks.forEach((chunk) => {
            this.consumeSseChunk(chunk, assistantMessage, requestId)
          })
        }

        if (buffer && this.isRequestActive(requestId)) {
          this.consumeSseChunk(buffer, assistantMessage, requestId)
        }
      } finally {
        if (!this.isRequestActive(requestId)) {
          try {
            await reader.cancel()
          } catch (e) {
            // 已中止或已关闭时忽略 reader cancel 异常
          }
        }
        try {
          reader.releaseLock()
        } catch (e) {
          // 部分浏览器在 reader 已关闭时会抛错，这里直接忽略
        }
      }
    },
    async extractErrorMessage(response, fallbackMessage) {
      try {
        const payload = await response.clone().json()
        return payload.msg || payload.message || fallbackMessage
      } catch (jsonError) {
        try {
          const text = await response.text()
          return text || fallbackMessage
        } catch (textError) {
          return fallbackMessage
        }
      }
    },
    buildHeaders() {
      const headers = {
        'Content-Type': 'application/json'
      }
      const token = getToken()
      if (token) {
        headers.Authorization = `Bearer ${token}`
      }
      return headers
    },
    consumeSseChunk(chunk, assistantMessage, requestId) {
      if (!this.isRequestActive(requestId)) {
        return
      }
      const lines = chunk.split(/\r?\n/)
      let data = ''

      lines.forEach((line) => {
        if (line.startsWith('data:')) {
          data += line.slice(5).trimStart()
        }
      })

      if (!data) {
        return
      }

      try {
        const payload = JSON.parse(data)
        if (payload.event === 'message' && payload.answer !== undefined && payload.answer !== null) {
          if (!this.isRequestActive(requestId)) {
            return
          }
          assistantMessage.content += payload.answer
          this.scrollToBottom()
          return
        }
        if (payload.msg || payload.message) {
          if (!this.isRequestActive(requestId)) {
            return
          }
          assistantMessage.content += payload.msg || payload.message
          this.scrollToBottom()
          return
        }
      } catch (e) {
        // 忽略 JSON 解析失败，走纯文本兜底
      }

      assistantMessage.content += data
      this.scrollToBottom()
    },
    hasActiveRequest() {
      return !!this.currentController
    },
    isRequestActive(requestId) {
      return !!this.currentController && this.currentRequestId === requestId
    },
    finishCurrentRequest(controller) {
      if (this.currentController === controller) {
        this.currentController = null
      }
      this.sending = false
      this.streaming = false
    },
    abortCurrentRequest(reason) {
      if (!this.currentController) {
        return
      }
      const controller = this.currentController
      this.currentController = null
      this.sending = false
      this.streaming = false
      controller.abort()
    },
    stopCurrentStream() {
      this.abortCurrentRequest('manual stop')
    },
    isAbortError(error) {
      return error && error.name === 'AbortError'
    },
    scrollToBottom() {
      this.$nextTick(() => {
        const container = this.$refs.messageListRef
        if (container) {
          container.scrollTop = container.scrollHeight
        }
      })
    }
  }
}
</script>

<style scoped>
.chat-page {
  min-height: calc(100vh - 84px);
  padding: 24px;
  background:
    radial-gradient(circle at top left, rgba(24, 144, 255, 0.14), transparent 32%),
    linear-gradient(180deg, #f6fbff 0%, #eef3f8 100%);
}

.chat-shell {
  max-width: 980px;
  height: calc(100vh - 132px);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(24, 144, 255, 0.12);
  border-radius: 24px;
  box-shadow: 0 20px 40px rgba(30, 60, 90, 0.08);
  backdrop-filter: blur(8px);
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 28px 20px;
  border-bottom: 1px solid #e8f1fb;
}

.header-main h2 {
  margin: 0;
  font-size: 28px;
  line-height: 1.2;
  color: #16324f;
}

.header-main p {
  margin: 8px 0 0;
  color: #5f7590;
  font-size: 14px;
}

.header-side {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 28px;
}

.message-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 18px;
  gap: 12px;
}

.message-row.is-user {
  flex-direction: row-reverse;
}

.avatar {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #16324f;
  background: linear-gradient(135deg, #d7e9ff 0%, #f7fbff 100%);
  border: 1px solid #c6ddfb;
}

.bubble {
  max-width: min(78%, 680px);
  padding: 14px 16px;
  border-radius: 18px;
  line-height: 1.7;
  font-size: 15px;
  white-space: pre-wrap;
  word-break: break-word;
  color: #1f3247;
  background: #ffffff;
  border: 1px solid #e6eef7;
  box-shadow: 0 10px 18px rgba(45, 76, 107, 0.06);
}

.message-row.is-user .bubble {
  color: #ffffff;
  background: linear-gradient(135deg, #1890ff 0%, #0f6fd1 100%);
  border-color: transparent;
}

.composer {
  padding: 20px 24px 24px;
  border-top: 1px solid #e8f1fb;
  background: rgba(250, 252, 255, 0.88);
}

.composer-actions {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tip {
  color: #72879e;
  font-size: 13px;
}

@media (max-width: 768px) {
  .chat-page {
    padding: 12px;
  }

  .chat-shell {
    height: calc(100vh - 104px);
    border-radius: 18px;
  }

  .chat-header,
  .message-list,
  .composer {
    padding-left: 16px;
    padding-right: 16px;
  }

  .chat-header {
    flex-direction: column;
  }

  .header-main h2 {
    font-size: 22px;
  }

  .bubble {
    max-width: 86%;
  }

  .composer-actions {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
