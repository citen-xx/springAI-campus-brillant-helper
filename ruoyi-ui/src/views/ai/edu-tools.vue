<template>
  <div class="app-container edu-tools">
    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="tool-card">
          <div slot="header" class="tool-header">
            <span>成绩查询</span>
            <el-tag size="mini">Function Calling</el-tag>
          </div>
          <el-form label-width="90px">
            <el-form-item label="学号">
              <el-input v-model="scoreForm.studentId" placeholder="请输入学号" />
            </el-form-item>
            <el-form-item label="课程">
              <el-input v-model="scoreForm.subject" placeholder="请输入课程名称，如 高数 / 英语 / Java" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="scoreLoading" @click="handleQueryScore">查询成绩</el-button>
            </el-form-item>
          </el-form>
          <el-alert
            v-if="scoreResult !== null"
            :title="`当前查询结果：${scoreResult} 分`"
            type="success"
            :closable="false"
            show-icon
          />
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="tool-card">
          <div slot="header" class="tool-header">
            <span>一卡通余额查询</span>
            <el-tag size="mini" type="success">Tool</el-tag>
          </div>
          <el-form label-width="90px">
            <el-form-item label="学号">
              <el-input v-model="balanceForm.studentId" placeholder="请输入学号" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="balanceLoading" @click="handleQueryBalance">查询余额</el-button>
            </el-form-item>
          </el-form>
          <el-alert
            v-if="balanceResult !== null"
            :title="`当前余额：${balanceResult} 元`"
            type="success"
            :closable="false"
            show-icon
          />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { getStudentScore, getCardBalance } from '@/api/system/edu'

export default {
  name: 'EduTools',
  data() {
    return {
      scoreForm: {
        studentId: '',
        subject: ''
      },
      balanceForm: {
        studentId: ''
      },
      scoreLoading: false,
      balanceLoading: false,
      scoreResult: null,
      balanceResult: null
    }
  },
  methods: {
    handleQueryScore() {
      if (!this.scoreForm.studentId || !this.scoreForm.subject) {
        this.$message.warning('请填写学号和课程名称')
        return
      }
      this.scoreLoading = true
      getStudentScore(this.scoreForm).then(response => {
        this.scoreResult = response.data
      }).finally(() => {
        this.scoreLoading = false
      })
    },
    handleQueryBalance() {
      if (!this.balanceForm.studentId) {
        this.$message.warning('请填写学号')
        return
      }
      this.balanceLoading = true
      getCardBalance(this.balanceForm).then(response => {
        this.balanceResult = response.data
      }).finally(() => {
        this.balanceLoading = false
      })
    }
  }
}
</script>

<style scoped>
.edu-tools {
  padding-bottom: 20px;
}

.tool-card {
  border-radius: 18px;
  min-height: 320px;
}

.tool-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
