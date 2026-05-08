<template>
  <div class="app-container ai-workbench">
    <div class="hero">
      <div class="hero-main">
        <h1>AI 工作台</h1>
        <p>统一查看和使用项目中的智能问答、知识文档、学生数据、教务工具与文档向量化能力。</p>
      </div>
      <el-tag type="success" size="medium">Spring AI + RAG</el-tag>
    </div>

    <el-row :gutter="16" class="cards">
      <el-col :xs="24" :sm="12" :lg="8" v-for="card in cards" :key="card.title">
        <div class="card-item">
          <div class="card-top">
            <div class="icon-wrap">
              <svg-icon :icon-class="card.icon" />
            </div>
            <div>
              <div class="card-title">{{ card.title }}</div>
              <div class="card-desc">{{ card.desc }}</div>
            </div>
          </div>
          <el-button type="primary" plain size="mini" @click="go(card.path)">进入</el-button>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="panel-header">
            <span>快速导入知识文档并向量化</span>
            <el-tag size="mini" type="warning">OSS + Vector Store</el-tag>
          </div>
          <el-form label-width="90px">
            <el-form-item label="文档名称">
              <el-input v-model="importForm.docName" placeholder="可选，默认使用文件名" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="importForm.remark" type="textarea" :rows="3" placeholder="可选" />
            </el-form-item>
            <el-form-item label="选择文件">
              <el-upload
                action="#"
                :auto-upload="false"
                :limit="1"
                :file-list="fileList"
                :http-request="dummyRequest"
                :on-change="handleFileChange"
                :on-remove="handleFileRemove"
              >
                <el-button slot="trigger" type="primary" size="small">选择文件</el-button>
                <div slot="tip" class="el-upload__tip">支持 PDF / Word / TXT / MD 等文档</div>
              </el-upload>
            </el-form-item>
            <el-form-item>
              <el-button type="success" :loading="importing" @click="submitImport">上传并向量化</el-button>
              <el-button @click="clearImport">重置</el-button>
            </el-form-item>
          </el-form>

          <el-alert
            v-if="importResult"
            :title="importResult.msg"
            type="success"
            show-icon
            :closable="false"
          >
            <div class="result-block">
              <div><strong>文档ID：</strong>{{ importResult.docId }}</div>
              <div><strong>原文件名：</strong>{{ importResult.originalFilename }}</div>
              <div><strong>文件URL：</strong>{{ importResult.url }}</div>
            </div>
          </el-alert>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="panel-header">
            <span>项目能力总览</span>
            <el-tag size="mini" type="info">Overview</el-tag>
          </div>
          <ul class="feature-list">
            <li>标准问答缓存与热点问答库管理</li>
            <li>知识文档上传、OSS 存储、自动向量化入库</li>
            <li>RAG 检索增强回答</li>
            <li>Function Calling：成绩查询 / 一卡通余额</li>
            <li>Redis 会话记忆与多轮对话</li>
            <li>Redis + Lua 滑动窗口限流</li>
            <li>SSE 流式输出与前端打字机效果</li>
          </ul>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { importKnowledgeFile } from '@/api/system/knowledge'

export default {
  name: 'AiWorkbench',
  data() {
    return {
      cards: [
        { title: '智能问答', desc: '带会话记忆、RAG 与工具调用的流式聊天页面', path: '/ai/chat', icon: 'message' },
        { title: '知识文档', desc: '查看知识文档、同步状态与向量化结果', path: '/ai/knowledge', icon: 'documentation' },
        { title: '问答库', desc: '管理标准问答、分类、关键词与缓存字段', path: '/ai/qa', icon: 'dict' },
        { title: '学生管理', desc: '维护学生基础信息，为工具调用和问答提供上下文', path: '/ai/student', icon: 'user' },
        { title: '教务工具', desc: '测试成绩查询和一卡通余额查询接口', path: '/ai/edu-tools', icon: 'education' }
      ],
      importForm: {
        docName: '',
        remark: ''
      },
      fileList: [],
      uploadFile: null,
      importing: false,
      importResult: null
    }
  },
  methods: {
    go(path) {
      this.$router.push(path)
    },
    dummyRequest() {
      // el-upload 关闭自动上传，无需真正请求
    },
    handleFileChange(file, fileList) {
      this.fileList = fileList.slice(-1)
      this.uploadFile = file.raw
      if (!this.importForm.docName) {
        const name = file.name || ''
        this.importForm.docName = name.replace(/\.[^.]+$/, '')
      }
    },
    handleFileRemove() {
      this.uploadFile = null
      this.fileList = []
    },
    clearImport() {
      this.importForm = {
        docName: '',
        remark: ''
      }
      this.importResult = null
      this.handleFileRemove()
    },
    submitImport() {
      if (!this.uploadFile) {
        this.$message.warning('请先选择文件')
        return
      }

      const formData = new FormData()
      formData.append('file', this.uploadFile)
      formData.append('docName', this.importForm.docName || '')
      formData.append('remark', this.importForm.remark || '')

      this.importing = true
      importKnowledgeFile(formData).then(response => {
        this.importResult = response
        this.$message.success('上传并向量化成功')
      }).finally(() => {
        this.importing = false
      })
    }
  }
}
</script>

<style scoped>
.ai-workbench {
  padding-bottom: 24px;
}

.hero {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 20px;
  padding: 24px;
  border-radius: 20px;
  background: linear-gradient(135deg, #f4fbff 0%, #eef7ff 50%, #f7fcff 100%);
  border: 1px solid #dcecff;
}

.hero-main h1 {
  margin: 0 0 10px;
  font-size: 30px;
  color: #17324d;
}

.hero-main p {
  margin: 0;
  color: #5f7590;
  line-height: 1.7;
}

.cards {
  margin-bottom: 18px;
}

.card-item {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 20px;
  border-radius: 18px;
  background: #fff;
  border: 1px solid #e8eef5;
  box-shadow: 0 10px 24px rgba(35, 60, 90, 0.05);
}

.card-top {
  display: flex;
  gap: 14px;
  margin-bottom: 18px;
}

.icon-wrap {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #edf6ff;
  color: #409eff;
  flex-shrink: 0;
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  color: #17324d;
  margin-bottom: 6px;
}

.card-desc {
  font-size: 13px;
  color: #6c819b;
  line-height: 1.6;
}

.panel-card {
  border-radius: 18px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.feature-list {
  margin: 0;
  padding-left: 20px;
  color: #475d76;
  line-height: 1.9;
}

.result-block {
  margin-top: 10px;
  line-height: 1.8;
  word-break: break-all;
}

@media (max-width: 992px) {
  .hero {
    flex-direction: column;
  }
}
</style>
