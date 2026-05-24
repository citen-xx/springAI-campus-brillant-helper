<template>
  <div class="app-container edu-tools">
    <el-row :gutter="16">
      <el-col v-if="isStudentRole" :xs="24" :lg="12">
        <el-card shadow="never" class="tool-card">
          <div slot="header" class="tool-header">
            <span>Student Score</span>
            <el-tag size="mini">Self Only</el-tag>
          </div>
          <el-alert
            title="Student requests only read the current login user's own score data."
            type="info"
            :closable="false"
            show-icon
            class="tool-tip"
          />
          <el-form label-width="90px">
            <el-form-item label="Subject">
              <el-input
                v-model="studentScoreForm.subject"
                placeholder="Example: 高等数学 / 大学英语 / Java程序设计"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="studentScoreLoading" @click="handleQueryMyScore">
                Query My Score
              </el-button>
            </el-form-item>
          </el-form>
          <el-alert
            v-if="studentScoreResult !== null"
            :title="`Current score: ${studentScoreResult}`"
            type="success"
            :closable="false"
            show-icon
          />
        </el-card>
      </el-col>

      <el-col v-if="isStudentRole" :xs="24" :lg="12">
        <el-card shadow="never" class="tool-card">
          <div slot="header" class="tool-header">
            <span>Student Card Balance</span>
            <el-tag size="mini" type="success">Self Only</el-tag>
          </div>
          <el-alert
            title="Student balance requests always return the current login student's own card balance."
            type="info"
            :closable="false"
            show-icon
            class="tool-tip"
          />
          <el-form label-width="90px">
            <el-form-item>
              <el-button type="primary" :loading="studentBalanceLoading" @click="handleQueryMyBalance">
                Query My Balance
              </el-button>
            </el-form-item>
          </el-form>
          <el-alert
            v-if="studentBalanceResult !== null"
            :title="`Current balance: ${studentBalanceResult}`"
            type="success"
            :closable="false"
            show-icon
          />
        </el-card>
      </el-col>
    </el-row>

    <el-row v-if="isAdminRole" :gutter="16" class="admin-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="tool-card admin-card">
          <div slot="header" class="tool-header">
            <span>Admin Score Query</span>
            <el-tag size="mini" type="warning">Admin Only</el-tag>
          </div>
          <el-alert
            title="Admin score queries call a dedicated admin API and do not reuse the student self-service API."
            type="warning"
            :closable="false"
            show-icon
            class="tool-tip"
          />
          <el-form label-width="90px">
            <el-form-item label="Student ID">
              <el-input v-model="adminScoreForm.studentId" placeholder="Enter student ID" />
            </el-form-item>
            <el-form-item label="Subject">
              <el-input v-model="adminScoreForm.subject" placeholder="Optional; empty means all scores" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="adminScoreLoading" @click="handleQueryAdminScore">
                Query Student Score
              </el-button>
            </el-form-item>
          </el-form>
          <el-table
            v-if="adminScoreResult"
            :data="adminScoreResult.scores || []"
            size="small"
            border
            class="result-table"
          >
            <el-table-column prop="studentId" label="Student ID" min-width="130" />
            <el-table-column prop="subject" label="Subject" min-width="120" />
            <el-table-column prop="score" label="Score" min-width="90" />
          </el-table>
          <div v-if="adminScoreResult" class="result-meta">
            <span>Name: {{ adminScoreResult.studentName || '-' }}</span>
            <span>Major: {{ adminScoreResult.majorCode || '-' }}</span>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="tool-card admin-card">
          <div slot="header" class="tool-header">
            <span>Admin Card Balance Query</span>
            <el-tag size="mini" type="danger">Admin Only</el-tag>
          </div>
          <el-alert
            title="Admin balance queries use a dedicated admin API. Role checks still happen on the server."
            type="warning"
            :closable="false"
            show-icon
            class="tool-tip"
          />
          <el-form label-width="90px">
            <el-form-item label="Student ID">
              <el-input v-model="adminBalanceForm.studentId" placeholder="Enter student ID" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="adminBalanceLoading" @click="handleQueryAdminBalance">
                Query Student Balance
              </el-button>
            </el-form-item>
          </el-form>
          <el-descriptions v-if="adminBalanceResult" :column="1" border size="small">
            <el-descriptions-item label="Student ID">{{ adminBalanceResult.studentId }}</el-descriptions-item>
            <el-descriptions-item label="Name">{{ adminBalanceResult.studentName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Major">{{ adminBalanceResult.majorCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Balance">{{ adminBalanceResult.balance }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-empty
      v-if="!isStudentRole && !isAdminRole"
      description="Current account does not have student or admin role."
    />
  </div>
</template>

<script>
import {
  getAdminCardBalance,
  getAdminScore,
  getMyCardBalance,
  getMyScore
} from '@/api/system/edu'

export default {
  name: 'EduTools',
  data() {
    return {
      studentScoreForm: {
        subject: ''
      },
      adminScoreForm: {
        studentId: '',
        subject: ''
      },
      adminBalanceForm: {
        studentId: ''
      },
      studentScoreLoading: false,
      studentBalanceLoading: false,
      adminScoreLoading: false,
      adminBalanceLoading: false,
      studentScoreResult: null,
      studentBalanceResult: null,
      adminScoreResult: null,
      adminBalanceResult: null
    }
  },
  computed: {
    roles() {
      return this.$store.getters.roles || []
    },
    isStudentRole() {
      return this.roles.includes('student')
    },
    isAdminRole() {
      return this.roles.includes('admin')
    }
  },
  methods: {
    handleQueryMyScore() {
      if (!this.studentScoreForm.subject) {
        this.$message.warning('Please input subject')
        return
      }
      this.studentScoreLoading = true
      getMyScore(this.studentScoreForm).then(response => {
        this.studentScoreResult = response.data
      }).finally(() => {
        this.studentScoreLoading = false
      })
    },
    handleQueryMyBalance() {
      this.studentBalanceLoading = true
      getMyCardBalance().then(response => {
        this.studentBalanceResult = response.data
      }).finally(() => {
        this.studentBalanceLoading = false
      })
    },
    handleQueryAdminScore() {
      if (!this.adminScoreForm.studentId) {
        this.$message.warning('Please input student ID')
        return
      }
      this.adminScoreLoading = true
      const params = {
        studentId: this.adminScoreForm.studentId
      }
      if (this.adminScoreForm.subject) {
        params.subject = this.adminScoreForm.subject
      }
      getAdminScore(params).then(response => {
        this.adminScoreResult = response.data
      }).finally(() => {
        this.adminScoreLoading = false
      })
    },
    handleQueryAdminBalance() {
      if (!this.adminBalanceForm.studentId) {
        this.$message.warning('Please input student ID')
        return
      }
      this.adminBalanceLoading = true
      getAdminCardBalance({
        studentId: this.adminBalanceForm.studentId
      }).then(response => {
        this.adminBalanceResult = response.data
      }).finally(() => {
        this.adminBalanceLoading = false
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
  margin-bottom: 16px;
}

.admin-row {
  margin-top: 8px;
}

.admin-card {
  border: 1px solid #f7d9a6;
}

.tool-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tool-tip {
  margin-bottom: 16px;
}

.result-table {
  margin-top: 8px;
}

.result-meta {
  margin-top: 12px;
  display: flex;
  gap: 16px;
  color: #606266;
  font-size: 13px;
  flex-wrap: wrap;
}
</style>
