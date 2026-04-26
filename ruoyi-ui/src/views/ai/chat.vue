<template>
  <div class="chat-container">
    <div class="chat-header">
      <h2>🤖 校园智能知识库助手</h2>
      <span class="subtitle">基于大模型与 RAG 技术驱动</span>
    </div>

    <div class="chat-main" ref="chatListRef">
      <div v-for="(msg, index) in messageList" :key="index"
        :class="['message-row', msg.role === 'user' ? 'row-right' : 'row-left']">
        <div class="avatar">{{ msg.role === 'user' ? '🧑‍🎓' : '🤖' }}</div>
        <div class="message-bubble">
          <template v-if="msg.role === 'user'">
            {{ msg.content }}
          </template>
          <div v-else class="markdown-body" v-html="renderMarkdown(msg.content)"></div>
        </div>
      </div>
    </div>

    <div class="chat-footer">
      <el-input v-model="inputText" type="textarea" :rows="3" placeholder="请输入你想咨询的问题，或让 AI 写一段代码试试..."
        @keyup.enter.native="sendMessage" resize="none" />
      <div class="send-btn-wrap">
        <el-button type="primary" size="medium" @click="sendMessage">发送消息 <i
            class="el-icon-s-promotion"></i></el-button>
      </div>
    </div>
  </div>
</template>

<script>
import { getToken } from '@/utils/auth'
// 引入 Markdown 解析和代码高亮插件
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/atom-one-dark.css' // 引入 Atom One Dark 暗黑风格代码主题

// 全局配置 Marked 解析器
marked.setOptions({
  highlight: function (code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value;
    }
    return hljs.highlightAuto(code).value;
  },
  langPrefix: 'hljs language-',
  breaks: true, // 允许回车自动换行
  gfm: true     // 支持 GitHub 风格的 Markdown (表格、删除线等)
});

export default {
  name: 'AiChat',
  data() {
    return {
      inputText: '',
      messageList: [
        { role: 'ai', content: '同学你好！我是校园智能助手。\n\n你可以问我规章制度，也可以让我**写一段代码**试试哦！' }
      ]
    }
  },
  methods: {
    // 将普通文本转为 HTML
    renderMarkdown(text) {
      if (!text) return '';
      return marked.parse(text);
    },

    async sendMessage() {
      if (!this.inputText.trim()) return;

      const question = this.inputText;
      this.messageList.push({ role: 'user', content: question });
      this.inputText = '';
      this.scrollToBottom();

      const aiMessage = { role: 'ai', content: '' };
      this.messageList.push(aiMessage);

      try {
        const response = await fetch(process.env.VUE_APP_BASE_API + '/system/ai/chat', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + getToken()
          },
          body: JSON.stringify({ query: question })
        });

        if (!response.ok) throw new Error('网络请求失败, 状态码: ' + response.status);

        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          const chunk = decoder.decode(value, { stream: true });
          buffer += chunk;

          const lines = buffer.split('\n');
          buffer = lines.pop();

          for (const line of lines) {
            if (line.startsWith('data:')) {
              const dataStr = line.substring(5).trim();
              if (!dataStr || dataStr === '[DONE]') continue;

              try {
                const data = JSON.parse(dataStr);
                if (data.answer !== undefined && data.answer !== null) {
                  aiMessage.content += data.answer;
                  this.scrollToBottom(); // 每次拼接新字，滚动条都沉底
                }
                else if (data.code || data.message) {
                  aiMessage.content += `\n**[系统提示：连接异常 - ${data.message || data.code}]**`;
                  this.scrollToBottom();
                }
              } catch (e) {
                // 忽略非 JSON 数据
              }
            }
          }
        }
      } catch (error) {
        aiMessage.content = '抱歉，大脑连接异常，请检查网络。';
      }
    },

    scrollToBottom() {
      this.$nextTick(() => {
        const chatList = this.$refs.chatListRef;
        if (chatList) {
          chatList.scrollTop = chatList.scrollHeight;
        }
      });
    }
  }
}
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  background-color: #f4f6f8;
  padding: 20px;
  box-sizing: border-box;
}

.chat-header {
  text-align: center;
  margin-bottom: 20px;
}

.chat-header h2 {
  margin: 0;
  color: #333;
}

.chat-header .subtitle {
  font-size: 12px;
  color: #888;
}

.chat-main {
  flex: 1;
  overflow-y: auto;
  background: #fff;
  border-radius: 10px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}

.message-row {
  display: flex;
  margin-bottom: 20px;
  align-items: flex-start;
}

.row-left {
  flex-direction: row;
}

.row-left .message-bubble {
  background-color: #f9fbfd;
  color: #333;
  margin-left: 15px;
  border-radius: 0 10px 10px 10px;
  border: 1px solid #eef2f5;
}

.row-right {
  flex-direction: row-reverse;
}

.row-right .message-bubble {
  background-color: #1890ff;
  color: #fff;
  margin-right: 15px;
  border-radius: 10px 0 10px 10px;
}

.avatar {
  font-size: 30px;
  width: 40px;
  height: 40px;
  line-height: 40px;
  text-align: center;
}

/* 气泡基础样式 */
.message-bubble {
  max-width: 75%;
  padding: 12px 18px;
  font-size: 15px;
  line-height: 1.6;
  word-wrap: break-word;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

/* =====================================
   🎯 专属 Markdown 渲染样式库
====================================== */
.markdown-body {
  font-size: 15px;
  color: #24292e;
}

/* 段落与列表 */
::v-deep .markdown-body p {
  margin-top: 0;
  margin-bottom: 10px;
}

::v-deep .markdown-body p:last-child {
  margin-bottom: 0;
}

::v-deep .markdown-body ul,
::v-deep .markdown-body ol {
  padding-left: 20px;
  margin-top: 5px;
  margin-bottom: 10px;
}

/* 行内代码块（文字中间的灰色小底纹代码） */
::v-deep .markdown-body code {
  background-color: rgba(27, 31, 35, 0.05);
  padding: 0.2em 0.4em;
  border-radius: 3px;
  font-family: SFMono-Regular, Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 85%;
  color: #e36209;
}

/* 多行代码块（极客黑框） */
::v-deep .markdown-body pre {
  background-color: #0d1117;
  /* 深色背景 */
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 10px 0;
}

::v-deep .markdown-body pre code {
  background-color: transparent;
  padding: 0;
  color: #c9d1d9;
  /* 浅色代码 */
  font-size: 14px;
}

/* 表格样式 */
::v-deep .markdown-body table {
  border-collapse: collapse;
  width: 100%;
  margin: 10px 0;
}

::v-deep .markdown-body th,
::v-deep .markdown-body td {
  border: 1px solid #dfe2e5;
  padding: 6px 13px;
}

::v-deep .markdown-body th {
  background-color: #f6f8fa;
  font-weight: 600;
}

.chat-footer {
  position: relative;
  background: #fff;
  border-radius: 10px;
  padding: 10px;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
}

.send-btn-wrap {
  position: absolute;
  right: 20px;
  bottom: 20px;
}
</style>
