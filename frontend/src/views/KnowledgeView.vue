<template>
  <div class="knowledge-container">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button type="primary" @click="dialogVisible = true">
          <el-icon><Upload /></el-icon>
          上传文档
        </el-button>
        
        <el-select
          v-model="filters.category"
          placeholder="选择分类"
          clearable
          style="width: 150px"
          @change="handleSearch"
        >
          <el-option
            v-for="cat in knowledgeStore.categories"
            :key="cat.id"
            :label="cat.name"
            :value="cat.name"
          />
        </el-select>
        
        <el-select
          v-model="filters.status"
          placeholder="选择状态"
          clearable
          style="width: 120px"
          @change="handleSearch"
        >
          <el-option label="活跃" value="ACTIVE" />
          <el-option label="已删除" value="DELETED" />
        </el-select>
      </div>
      
      <el-input
        v-model="filters.keyword"
        placeholder="搜索文档..."
        style="width: 300px"
        clearable
        @keyup.enter="handleSearch"
      >
        <template #append>
          <el-button :icon="Search" @click="handleSearch" />
        </template>
      </el-input>
    </div>

    <!-- 文档列表 -->
    <div class="document-list">
      <el-table
        v-loading="knowledgeStore.loading"
        :data="knowledgeStore.documents"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="fileType" label="类型" width="80" />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column label="处理状态" width="120">
          <template #default="{ row }">
            <el-tag
              :type="getStatusType(row.processStatus)"
              size="small"
            >
              {{ getStatusText(row.processStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="vectorCount" label="向量数" width="100" />
        <el-table-column label="上传时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              @click="handleReindex(row)"
            >
              重新索引
            </el-button>
            <el-button
              link
              type="danger"
              size="small"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="knowledgeStore.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="handleSearch"
        />
      </div>
    </div>

    <!-- 上传对话框 -->
    <el-dialog
      v-model="dialogVisible"
      title="上传文档"
      width="500px"
      @close="handleDialogClose"
    >
      <el-form :model="uploadForm" label-width="80px">
        <el-form-item label="文档标题" required>
          <el-input v-model="uploadForm.title" placeholder="请输入文档标题" />
        </el-form-item>
        
        <el-form-item label="分类" required>
          <el-select
            v-model="uploadForm.category"
            placeholder="请选择分类"
            style="width: 100%"
          >
            <el-option
              v-for="cat in knowledgeStore.categories"
              :key="cat.id"
              :label="cat.name"
              :value="cat.name"
            />
          </el-select>
        </el-form-item>
        
        <el-form-item label="描述">
          <el-input
            v-model="uploadForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入文档描述（可选）"
          />
        </el-form-item>
        
        <el-form-item label="标签">
          <el-input
            v-model="uploadForm.tags"
            placeholder="多个标签用逗号分隔"
          />
        </el-form-item>
        
        <el-form-item label="选择文件" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            accept=".pdf,.txt,.md,.docx,.doc,.ppt,.pptx"
          >
            <el-button>选择文件</el-button>
            <template #tip>
              <div class="upload-tip">
                支持PDF、Word、PPT、Markdown、TXT格式，大小不超过50MB
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="uploading"
          :disabled="!uploadForm.file"
          @click="handleUpload"
        >
          上传
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useKnowledgeStore } from '@/stores/knowledge'
import { uploadDocument, deleteDocument, reindexDocument } from '@/api/knowledge'

const knowledgeStore = useKnowledgeStore()

const filters = reactive({
  category: '',
  status: '',
  keyword: ''
})

const pagination = reactive({
  page: 1,
  size: 20
})

const dialogVisible = ref(false)
const uploading = ref(false)
const uploadRef = ref(null)

const uploadForm = reactive({
  title: '',
  category: '',
  description: '',
  tags: '',
  file: null
})

onMounted(async () => {
  await knowledgeStore.loadCategories()
  await handleSearch()
})

// 搜索
async function handleSearch() {
  await knowledgeStore.loadDocuments({
    ...pagination,
    ...filters
  })
}

// 文件选择
function handleFileChange(file) {
  uploadForm.file = file.raw
  if (!uploadForm.title) {
    uploadForm.title = file.name.replace(/\.[^/.]+$/, '')
  }
}

// 文件移除
function handleFileRemove() {
  uploadForm.file = null
}

// 上传文档
async function handleUpload() {
  if (!uploadForm.title) {
    ElMessage.warning('请输入文档标题')
    return
  }
  if (!uploadForm.category) {
    ElMessage.warning('请选择分类')
    return
  }
  if (!uploadForm.file) {
    ElMessage.warning('请选择文件')
    return
  }
  
  uploading.value = true
  
  try {
    const formData = new FormData()
    formData.append('file', uploadForm.file)
    formData.append('title', uploadForm.title)
    formData.append('category', uploadForm.category)
    if (uploadForm.description) {
      formData.append('description', uploadForm.description)
    }
    if (uploadForm.tags) {
      formData.append('tags', uploadForm.tags)
    }
    
    await uploadDocument(formData)
    ElMessage.success('上传成功，正在处理文档...')
    dialogVisible.value = false
    await handleSearch()
  } catch (error) {
    ElMessage.error('上传失败，请重试')
  } finally {
    uploading.value = false
  }
}

// 对话框关闭
function handleDialogClose() {
  Object.assign(uploadForm, {
    title: '',
    category: '',
    description: '',
    tags: '',
    file: null
  })
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
}

// 重新索引
async function handleReindex(row) {
  try {
    await ElMessageBox.confirm('确认要重新索引这个文档吗？', '提示', {
      type: 'warning'
    })
    
    await reindexDocument(row.id)
    ElMessage.success('已提交重新索引任务')
    await handleSearch()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

// 删除文档
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确认要删除这个文档吗？删除后无法恢复。', '警告', {
      type: 'warning',
      confirmButtonText: '确定删除'
    })
    
    await deleteDocument(row.id)
    ElMessage.success('删除成功')
    await handleSearch()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 格式化文件大小
function formatFileSize(bytes) {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

// 格式化时间
function formatTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

// 状态类型
function getStatusType(status) {
  const map = {
    'PENDING': 'info',
    'PROCESSING': 'warning',
    'COMPLETED': 'success',
    'FAILED': 'danger'
  }
  return map[status] || 'info'
}

// 状态文本
function getStatusText(status) {
  const map = {
    'PENDING': '待处理',
    'PROCESSING': '处理中',
    'COMPLETED': '已完成',
    'FAILED': '失败'
  }
  return map[status] || status
}
</script>

<style scoped>
.knowledge-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 20px;
  background: #fff;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.toolbar-left {
  display: flex;
  gap: 12px;
}

.document-list {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.upload-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}
</style>
