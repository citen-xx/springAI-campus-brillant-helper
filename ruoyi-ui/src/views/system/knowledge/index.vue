<template>
  <div class="app-container">
    <el-form
      :model="queryParams"
      ref="queryForm"
      size="small"
      :inline="true"
      v-show="showSearch"
      label-width="80px"
    >
      <el-form-item label="文档名称" prop="docName">
        <el-input
          v-model="queryParams.docName"
          placeholder="请输入文档名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['system:knowledge:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['system:knowledge:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['system:knowledge:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['system:knowledge:export']"
        >导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-upload2"
          size="mini"
          @click="handleImport"
          v-hasPermi="['system:knowledge:add']"
        >导入文件并向量化</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="knowledgeList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="文档ID" align="center" prop="docId" width="100" />
      <el-table-column label="文档名称" align="center" prop="docName" :show-overflow-tooltip="true" />
      <el-table-column label="文件路径" align="center" prop="fileUrl" :show-overflow-tooltip="true" />
      <el-table-column label="同步状态" align="center" prop="status" width="110">
        <template slot-scope="scope">
          <el-tag size="small" :type="statusTagType(scope.row.status)">
            {{ statusText(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注说明" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="180">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['system:knowledge:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['system:knowledge:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog :title="title" :visible.sync="open" width="520px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="文档名称" prop="docName">
          <el-input v-model="form.docName" placeholder="请输入文档名称" />
        </el-form-item>
        <el-form-item label="文件路径" prop="fileUrl">
          <el-input v-model="form.fileUrl" placeholder="请输入 OSS 文件 URL" />
        </el-form-item>
        <el-form-item label="备注说明" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <el-dialog title="导入知识文件并向量化" :visible.sync="importOpen" width="560px" append-to-body>
      <el-form ref="importFormRef" :model="importForm" label-width="90px">
        <el-form-item label="文档名称">
          <el-input v-model="importForm.docName" placeholder="可选，默认使用文件名" />
        </el-form-item>
        <el-form-item label="备注说明">
          <el-input v-model="importForm.remark" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
        <el-form-item label="选择文件" required>
          <el-upload
            ref="uploadRef"
            action="#"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :file-list="fileList"
            :http-request="dummyRequest"
          >
            <el-button slot="trigger" size="small" type="primary">选择文件</el-button>
            <div slot="tip" class="el-upload__tip">支持 PDF / Word / TXT / MD 等文档</div>
          </el-upload>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" :loading="importing" @click="submitImport">上 传 并 向 量 化</el-button>
        <el-button @click="cancelImport">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  listKnowledge,
  getKnowledge,
  delKnowledge,
  addKnowledge,
  updateKnowledge,
  importKnowledgeFile
} from '@/api/system/knowledge'

export default {
  name: 'Knowledge',
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      knowledgeList: [],
      title: '',
      open: false,
      importOpen: false,
      importing: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        docName: null,
        fileUrl: null,
        status: null
      },
      form: {},
      importForm: {
        docName: '',
        remark: ''
      },
      fileList: [],
      uploadFile: null,
      rules: {
        docName: [
          { required: true, message: '文档名称不能为空', trigger: 'blur' }
        ],
        fileUrl: [
          { required: true, message: '文件路径不能为空', trigger: 'blur' }
        ]
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listKnowledge(this.queryParams).then(response => {
        this.knowledgeList = response.rows
        this.total = response.total
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    statusText(status) {
      if (status === '1') return '同步中'
      if (status === '2') return '已同步'
      return '未同步'
    },
    statusTagType(status) {
      if (status === '1') return 'warning'
      if (status === '2') return 'success'
      return 'info'
    },
    cancel() {
      this.open = false
      this.reset()
    },
    reset() {
      this.form = {
        docId: null,
        docName: null,
        fileUrl: null,
        status: null,
        createBy: null,
        createTime: null,
        updateBy: null,
        updateTime: null,
        remark: null
      }
      this.resetForm('form')
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm('queryForm')
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.docId)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '新增知识文档'
    },
    handleUpdate(row) {
      this.reset()
      const docId = row.docId || this.ids
      getKnowledge(docId).then(response => {
        this.form = response.data
        this.open = true
        this.title = '修改知识文档'
      })
    },
    submitForm() {
      this.$refs['form'].validate(valid => {
        if (!valid) {
          return
        }
        if (this.form.docId != null) {
          updateKnowledge(this.form).then(() => {
            this.$modal.msgSuccess('修改成功')
            this.open = false
            this.getList()
          })
        } else {
          addKnowledge(this.form).then(() => {
            this.$modal.msgSuccess('新增成功')
            this.open = false
            this.getList()
          })
        }
      })
    },
    handleDelete(row) {
      const docIds = row.docId || this.ids
      this.$modal.confirm('是否确认删除知识文档编号为"' + docIds + '"的数据项？').then(() => {
        return delKnowledge(docIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess('删除成功')
      }).catch(() => {})
    },
    handleExport() {
      this.download('system/knowledge/export', {
        ...this.queryParams
      }, `knowledge_${new Date().getTime()}.xlsx`)
    },
    handleImport() {
      this.importOpen = true
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
    cancelImport() {
      this.importOpen = false
      this.importing = false
      this.importForm = {
        docName: '',
        remark: ''
      }
      this.handleFileRemove()
    },
    dummyRequest() {
      // el-upload 关闭自动上传，这里无需真正请求
    },
    submitImport() {
      if (!this.uploadFile) {
        this.$modal.msgWarning('请先选择文件')
        return
      }
      const formData = new FormData()
      formData.append('file', this.uploadFile)
      formData.append('docName', this.importForm.docName || '')
      formData.append('remark', this.importForm.remark || '')

      this.importing = true
      importKnowledgeFile(formData).then(() => {
        this.$modal.msgSuccess('上传并向量化成功')
        this.importOpen = false
        this.importing = false
        this.getList()
        this.cancelImport()
      }).catch(() => {
        this.importing = false
      })
    }
  }
}
</script>
