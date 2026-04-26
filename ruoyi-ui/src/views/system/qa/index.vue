<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="分类" prop="category">
        <el-input
          v-model="queryParams.category"
          placeholder="请输入分类"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="关键词" prop="keywords">
        <el-input
          v-model="queryParams.keywords"
          placeholder="请输入关键词"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="查询次数" prop="hitCount">
        <el-input
          v-model="queryParams.hitCount"
          placeholder="请输入查询次数"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="是否热门" prop="isHot">
        <el-select v-model="queryParams.isHot" placeholder="请选择热门状态" clearable>
          <el-option label="否" value="0"/>
          <el-option label="是" value="1"/>
        </el-select>
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
          v-hasPermi="['system:qa:add']"
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
          v-hasPermi="['system:qa:edit']"
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
          v-hasPermi="['system:qa:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['system:qa:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="qaList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ID" align="center" prop="qaId" width="60" />
      <el-table-column label="标准问题" align="left" prop="question" :show-overflow-tooltip="true" min-width="150" />
      <el-table-column label="标准答案" align="left" prop="answer" :show-overflow-tooltip="true" min-width="200" />
      <el-table-column label="分类" align="center" prop="category" width="100">
        <template slot-scope="scope">
          <el-tag size="small">{{ scope.row.category || '默认' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="关键词" align="center" prop="keywords" :show-overflow-tooltip="true" />
      <el-table-column label="查询次数" align="center" prop="hitCount" width="80" />
      <el-table-column label="热门" align="center" prop="isHot" width="80">
        <template slot-scope="scope">
          <el-tag :type="scope.row.isHot == 1 ? 'danger' : 'info'" size="small">
            {{ scope.row.isHot == 1 ? '🔥热门' : '普通' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="80">
         <template slot-scope="scope">
          <el-tag :type="scope.row.status == 0 ? 'success' : 'danger'" size="small">
            {{ scope.row.status == 0 ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="150">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['system:qa:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['system:qa:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改AI校园热点问答库对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="650px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="标准问题" prop="question">
          <el-input v-model="form.question" type="textarea" :rows="3" placeholder="请输入标准问题描述" />
        </el-form-item>
        <el-form-item label="标准答案" prop="answer">
          <el-input v-model="form.answer" type="textarea" :rows="5" placeholder="请输入标准答案内容(支持Markdown)" />
        </el-form-item>
        <el-row>
          <el-col :span="12">
            <el-form-item label="分类" prop="category">
              <el-input v-model="form.category" placeholder="如：教务、生活、奖学金" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关键词" prop="keywords">
              <el-input v-model="form.keywords" placeholder="逗号分隔，精准匹配" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
           <el-col :span="12">
            <el-form-item label="查询次数" prop="hitCount">
              <el-input-number v-model="form.hitCount" :min="0" placeholder="请输入查询次数" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否热门" prop="isHot">
              <el-radio-group v-model="form.isHot">
                <el-radio :label="1">是</el-radio>
                <el-radio :label="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="缓存时间(秒)" prop="cacheTtl">
              <el-input-number v-model="form.cacheTtl" :min="0" placeholder="默认无需填写" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
             <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio :label="0">正常</el-radio>
                <el-radio :label="1">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入相关备注" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listQa, getQa, delQa, addQa, updateQa } from "@/api/system/qa"

export default {
  name: "Qa",
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // AI校园热点问答库表格数据
      qaList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        question: null,
        answer: null,
        category: null,
        keywords: null,
        hitCount: null,
        isHot: null,
        cacheTtl: null,
        status: null,
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        question: [
          { required: true, message: "标准问题描述不能为空", trigger: "blur" }
        ],
        answer: [
          { required: true, message: "标准答案内容(支持Markdown)不能为空", trigger: "blur" }
        ],
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    /** 查询AI校园热点问答库列表 */
    getList() {
      this.loading = true
      listQa(this.queryParams).then(response => {
        this.qaList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      this.form = {
        qaId: null,
        question: null,
        answer: null,
        category: null,
        keywords: null,
        hitCount: null,
        isHot: null,
        cacheTtl: null,
        status: null,
        createBy: null,
        createTime: null,
        updateTime: null,
        remark: null
      }
      this.resetForm("form")
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm")
      this.handleQuery()
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.qaId)
      this.single = selection.length!==1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset()
      this.open = true
      this.title = "添加AI校园热点问答库"
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      const qaId = row.qaId || this.ids
      getQa(qaId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改AI校园热点问答库"
      })
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.qaId != null) {
            updateQa(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addQa(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const qaIds = row.qaId || this.ids
      this.$modal.confirm('是否确认删除AI校园热点问答库编号为"' + qaIds + '"的数据项？').then(function() {
        return delQa(qaIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('system/qa/export', {
        ...this.queryParams
      }, `qa_${new Date().getTime()}.xlsx`)
    }
  }
}
</script>
