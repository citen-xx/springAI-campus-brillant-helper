<template>
  <div class="chat-page">
    <div class="chat-shell">
      <div class="chat-header">
        <div>
          <h2>AI Stream Chat</h2>
          <p>Using fetch + ReadableStream to render SSE output word by word.</p>
        </div>
        <el-tag size="small" type="success">SSE</el-tag>
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
          placeholder="Type your message and watch the reply stream back from the server."
          @keyup.ctrl.enter.native="sendMessage"
        />
        <div class="composer-actions">
          <span class="tip">Press Ctrl + Enter to send</span>
          <el-button type="primary" :loading="sending" @click="sendMessage">Send</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { getToken } from '@/utils/auth'

export default {
  name: 'AiChat',
  data() {
    return {
      inputText: '',
      sending: false,
      messages: [
        {
          role: 'assistant',
          content: 'Welcome. Ask any question and the server will stream back a mock LLM response.'
        }
      ]
    }
  },
  methods: {
    async sendMessage() {
      const prompt = this.inputText.trim()
      if (!prompt || this.sending) {
        return
      }

      this.messages.push({ role: 'user', content: prompt })
      const assistantMessage = { role: 'assistant', content: '' }
      this.messages.push(assistantMessage)
      this.inputText = ''
      this.sending = true
      this.scrollToBottom()

      try {
        const response = await fetch(`${process.env.VUE_APP_BASE_API}/api/ai/chat/stream`, {
          method: 'POST',
          headers: this.buildHeaders(),
          body: JSON.stringify({ prompt })
        })

        if (!response.ok) {
          throw new Error(await this.extractErrorMessage(response, `Request failed with status ${response.status}`))
        }

        if (!response.body) {
          throw new Error('大模型流式响应不可用，请稍后再试')
        }

        const contentType = (response.headers.get('content-type') || '').toLowerCase()
        if (!contentType.includes('text/event-stream')) {
          throw new Error(await this.extractErrorMessage(response, '大模型额度已耗尽，请稍后再试'))
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder('utf-8')
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) {
            break
          }

          buffer += decoder.decode(value, { stream: true })
          const chunks = buffer.split(/\r?\n\r?\n/)
          buffer = chunks.pop() || ''

          chunks.forEach((chunk) => {
            this.consumeSseChunk(chunk, assistantMessage)
          })
        }

        if (buffer) {
          this.consumeSseChunk(buffer, assistantMessage)
        }
      } catch (error) {
        const message = error.message || '大模型额度已耗尽，请稍后再试'
        assistantMessage.content = message
        this.$message.error(message)
      } finally {
        this.sending = false
        this.scrollToBottom()
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
    consumeSseChunk(chunk, assistantMessage) {
      const lines = chunk.split(/\r?\n/)
      let data = ''

      lines.forEach((line) => {
        if (line.startsWith('data:')) {
          data += line.slice(5).trimStart()
        }
      })

      if (data) {
        assistantMessage.content += data
        this.scrollToBottom()
      }
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
  background: rgba(255, 255, 255, 0.9);
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
  padding: 24px 28px 20px;
  border-bottom: 1px solid #e8f1fb;
}

.chat-header h2 {
  margin: 0;
  font-size: 28px;
  line-height: 1.2;
  color: #16324f;
}

.chat-header p {
  margin: 8px 0 0;
  color: #5f7590;
  font-size: 14px;
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

  .chat-header h2 {
    font-size: 22px;
  }

  .bubble {
    max-width: 86%;
  }
}
</style>
