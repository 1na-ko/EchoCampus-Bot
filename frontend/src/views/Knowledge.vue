<template>
  <div class="knowledge-view">
    <!-- Immersive Background -->
    <div class="immersive-bg">
      <div class="bg-shape shape-1"></div>
      <div class="bg-shape shape-2"></div>
    </div>

    <div class="content-wrapper">
      
      <!-- Header Section -->
      <div class="glass-header">
        <div class="header-content">
          <div class="title-group">
            <h1 class="page-title">知识库中心</h1>
          </div>
          <a-button type="primary" size="large" class="action-btn-primary" @click="showUploadModal = true">
            <template #icon><UploadOutlined /></template>
            上传文档
          </a-button>
        </div>

        <!-- Filter / Toolbar -->
        <div class="toolbar-container">
           <div class="search-box">
             <SearchOutlined class="search-icon" />
             <input 
                v-model="keyword" 
                placeholder="搜索文档名称、标签..." 
                class="transparent-input"
                @keypress.enter="handleSearch"
             />
           </div>
           
           <div class="filter-actions">
              <a-select
                v-model:value="filterCategory"
                placeholder="全部分类"
                class="glass-select"
                :bordered="false"
                allowClear
                @change="handleFilterChange"
                dropdownClassName="glass-dropdown"
              >
                <a-select-option value="">全部分类</a-select-option>
                <a-select-option v-for="cat in knowledgeStore.categories" :key="cat.id" :value="cat.name">
                  {{ cat.name }}
                </a-select-option>
              </a-select>

              <a-select
                v-model:value="filterStatus"
                placeholder="状态"
                class="glass-select small-width"
                :bordered="false"
                allowClear
                @change="handleFilterChange"
                dropdownClassName="glass-dropdown"
              >
                <a-select-option value="">全部状态</a-select-option>
                <a-select-option value="ACTIVE">正常</a-select-option>
                <a-select-option value="PROCESSING">处理中</a-select-option>
                <a-select-option value="FAILED">失败</a-select-option>
              </a-select>
           </div>
        </div>
      </div>

      <!-- Document Grid -->
      <div class="scroll-area">
        <a-spin :spinning="knowledgeStore.isLoading" size="large">
          <div class="grid-layout" v-if="knowledgeStore.documents.length > 0">
            <div
              v-for="doc in knowledgeStore.documents"
              :key="doc.id"
              class="knowledge-card"
            >
              <!-- Card Header -->
              <div class="card-header">
                <div class="icon-wrapper" :style="{ color: getFileIconColor(doc.fileType) }">
                   <div class="icon-bg" :style="{ backgroundColor: getFileIconColor(doc.fileType), opacity: 0.1 }"></div>
                   <component :is="getFileIcon(doc.fileType)" />
                </div>
                <div class="card-actions">
                   <a-dropdown :trigger="['click']" placement="bottomRight">
                      <a-button type="text" shape="circle" class="more-btn">
                        <MoreOutlined />
                      </a-button>
                      <template #overlay>
                        <a-menu class="custom-menu">
                          <a-menu-item @click="handleViewDoc(doc)">
                            <EyeOutlined /> 查看详情
                          </a-menu-item>
                          <a-menu-item @click="handleEditDoc(doc)">
                            <EditOutlined /> 编辑信息
                          </a-menu-item>
                          <a-menu-item @click="handleReindex(doc.id)">
                            <ReloadOutlined /> 重新索引
                          </a-menu-item>
                          <a-menu-divider />
                          <a-menu-item danger @click="handleDeleteDoc(doc.id)">
                            <DeleteOutlined /> 删除文档
                          </a-menu-item>
                        </a-menu>
                      </template>
                   </a-dropdown>
                </div>
              </div>

              <!-- Card Body -->
              <div class="card-body">
                 <div class="status-pill" :class="doc.status.toLowerCase()">
                   <component :is="getStatusIcon(doc.status)" spin v-if="doc.status === 'PROCESSING'" />
                   <component :is="getStatusIcon(doc.status)" v-else />
                   {{ getStatusText(doc.status) }}
                   <div class="processing-bar" v-if="doc.status === 'PROCESSING'">
                     <div class="bar-inner"></div>
                   </div>
                 </div>
                 <h3 class="doc-name" :title="doc.title">{{ doc.title }}</h3>
                 <p class="doc-snippet">{{ doc.description || '暂无描述信息...' }}</p>
              </div>

              <!-- Card Footer -->
              <div class="card-footer">
                 <div class="meta-info">
                   <span class="meta-tag">{{ doc.fileType.toUpperCase() }}</span>
                   <span class="meta-dot">·</span>
                   <span>{{ formatFileSize(doc.fileSize) }}</span>
                 </div>
                 <div class="meta-info">
                     <span class="vector-count" title="向量数量">
                        <ApartmentOutlined class="vector-icon" />
                        {{ doc.vectorCount || 0 }}
                     </span>
                     <span class="meta-dot">·</span>
                     <div class="date-info">{{ formatTime(doc.createdAt).split(' ')[0] }}</div>
                 </div>
              </div>

              <!-- Hover Effect Overlay -->
              <div class="hover-overlay"></div>
            </div>
          </div>

          <!-- Empty State -->
          <div v-else-if="!knowledgeStore.isLoading" class="empty-placeholder">
             <div class="empty-illustration">
                <FileOutlined />
             </div>
             <h3>知识库空空如也</h3>
             <p>开始构建您的第一个知识集合吧</p>
             <a-button type="primary" size="large" class="action-btn-primary" @click="showUploadModal = true">
                立即上传
             </a-button>
          </div>

          <!-- Pagination -->
          <div v-if="knowledgeStore.total > 0" class="pagination-bar">
             <a-pagination
                v-model:current="knowledgeStore.page"
                v-model:pageSize="knowledgeStore.size"
                :total="knowledgeStore.total"
                show-less-items
                @change="handlePageChange"
              />
          </div>
        </a-spin>
      </div>
    </div>
    
    <!-- Upload Modal -->
    <a-modal
      v-model:open="showUploadModal"
      title="上传知识库文档"
      centered
      :confirm-loading="uploading"
      :footer="showProgress ? null : undefined"
      @ok="handleUpload"
      @cancel="handleUploadModalClose"
      width="520px"
      :maskClosable="!showProgress"
      :closable="!showProgress || progressCompleted || progressFailed"
      wrapClassName="elegant-modal-wrap"
      :bodyStyle="{ padding: '0' }"
    >
      <div v-show="!showProgress" class="upload-form-container" style="padding: 24px;">
        <a-form :model="uploadForm" layout="vertical">
          <a-form-item label="选择文件" required>
               <a-upload
                  v-model:file-list="fileList"
                  :before-upload="beforeUpload"
                  :max-count="1"
                  :disabled="showProgress"
                  accept=".pdf,.txt,.md,.markdown,.docx,.doc,.pptx,.ppt,.xlsx,.xls"
                  list-type="text"
                  class="elegant-upload-clean"
               >
                  <div style="display: flex; flex-direction: column; align-items: flex-start;">
                    <a-button :disabled="showProgress" class="upload-trigger-btn">
                       <UploadOutlined /> 选择文件
                    </a-button>
                    <span class="upload-hint">支持 PDF, Markdown, Word, PPT, Excel 等文档格式（最大 50MB）</span>
                  </div>
               </a-upload>
          </a-form-item>
          
          <a-form-item label="文档标题" required>
            <a-input v-model:value="uploadForm.title" placeholder="给文档起个好名字" />
          </a-form-item>
  
          <a-form-item label="分类" required>
             <a-select v-model:value="uploadForm.category" placeholder="选择分类" allowClear>
              <a-select-option v-for="cat in knowledgeStore.categories" :key="cat.id" :value="cat.name">{{ cat.name }}</a-select-option>
            </a-select>
          </a-form-item>

          <a-form-item label="文档描述">
            <a-textarea v-model:value="uploadForm.description" :rows="3" placeholder="简要描述文档内容..." />
          </a-form-item>
  
           <a-form-item label="标签">
            <a-input v-model:value="uploadForm.tags" placeholder="使用逗号分隔多个标签" />
          </a-form-item>
        </a-form>
      </div>
      
      <div v-if="showProgress" class="upload-progress-view">
        <!-- 移除了重复的标题和图标，直接显示进度组件 -->
        <UploadProgress
          ref="uploadProgressRef"
          :doc-id="uploadingDocId"
          :visible="showProgress"
          :show-header-close="false"
          @close="handleProgressClose"
          @completed="handleUploadCompleted"
          @failed="handleUploadFailed"
          class="embedded-progress"
        />
        
        <div v-if="progressCompleted || progressFailed" class="progress-actions">
           <a-button type="primary" @click="handleUploadDone">完成并关闭</a-button>
        </div>
      </div>
    </a-modal>

      <!-- Reindex Progress Modal -->
    <a-modal
      v-model:open="showReindexModal"
      title="索引重建进度"
      :footer="null"
      width="500px"
      :closable="false"
      :maskClosable="reindexCompleted || reindexFailed"
      wrapClassName="elegant-modal-wrap"
      :bodyStyle="{ padding: '0' }"
      centered
    >
      <div class="upload-progress-view compact" style="min-height: auto; padding: 24px;">
         <!-- 移除了重复的标题和图标 -->
         <UploadProgress
            ref="reindexProgressRef"
            :doc-id="reindexingDocId"
            :visible="showReindexProgress"
            :show-header-close="false"
            @close="handleReindexClose"
            @completed="handleReindexCompleted"
            @failed="handleReindexFailed"
            class="embedded-progress"
          />
          <div v-if="reindexCompleted || reindexFailed" class="progress-actions">
             <a-button type="primary" @click="showReindexModal = false">关闭窗口</a-button>
          </div>
      </div>
    </a-modal>

    <!-- Edit Modal -->
    <a-modal v-model:open="showEditModal" title="编辑文档信息" @ok="handleUpdateDoc" width="500px" wrapClassName="elegant-modal-wrap">
       <a-form :model="editForm" layout="vertical">
        <a-form-item label="文档标题" required>
          <a-input v-model:value="editForm.title" />
        </a-form-item>
        <a-form-item label="文档描述">
          <a-textarea v-model:value="editForm.description" :rows="3" />
        </a-form-item>
        <a-form-item label="分类">
          <a-select v-model:value="editForm.category" allowClear>
            <a-select-option v-for="cat in knowledgeStore.categories" :key="cat.id" :value="cat.name">{{ cat.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="标签">
          <a-input v-model:value="editForm.tags" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Detail Modal -->
    <a-modal v-model:open="showDetailModal" title="文档详情" :footer="null" width="600px" wrapClassName="elegant-modal-wrap">
      <a-descriptions v-if="currentDoc" bordered :column="1" size="middle">
        <a-descriptions-item label="标题">{{ currentDoc.title }}</a-descriptions-item>
        <a-descriptions-item label="描述">{{ currentDoc.description || '暂无' }}</a-descriptions-item>
        <a-descriptions-item label="文件名">{{ currentDoc.fileName }}</a-descriptions-item>
        <a-descriptions-item label="文件大小">{{ formatFileSize(currentDoc.fileSize) }}</a-descriptions-item>
        <a-descriptions-item label="文件类型">{{ currentDoc.fileType.toUpperCase() }}</a-descriptions-item>
        <a-descriptions-item label="分类">{{ currentDoc.category || '未分类' }}</a-descriptions-item>
        <a-descriptions-item label="标签">{{ currentDoc.tags || '无' }}</a-descriptions-item>
        <a-descriptions-item label="状态">
           <a-tag :color="getStatusColor(currentDoc.status)">{{ getStatusText(currentDoc.status) }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="向量数量">{{ currentDoc.vectorCount || 0 }}</a-descriptions-item>
        <a-descriptions-item label="处理状态">{{ currentDoc.processStatus || '未知' }}</a-descriptions-item>
        <a-descriptions-item label="上传时间">{{ dayjs(currentDoc.createdAt).format('YYYY-MM-DD HH:mm:ss') }}</a-descriptions-item>
        <a-descriptions-item label="更新时间">{{ dayjs(currentDoc.updatedAt).format('YYYY-MM-DD HH:mm:ss') }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { Modal, message } from 'ant-design-vue'
import { useKnowledgeStore } from '@/stores/knowledge'
import type { KnowledgeDoc, KnowledgeDocRequest } from '@/types'
import dayjs from 'dayjs'
import {
  UploadOutlined,
  FileOutlined,
  MoreOutlined,
  EyeOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  FilePdfOutlined,
  FileWordOutlined,
  FileMarkdownOutlined,
  FileTextOutlined,
  FilePptOutlined,
  FileExcelOutlined,
  SearchOutlined,
  CheckCircleFilled,
  SyncOutlined,
  CloseCircleFilled,
  ApartmentOutlined,
} from '@ant-design/icons-vue'
import UploadProgress from '@/components/UploadProgress.vue'

const knowledgeStore = useKnowledgeStore()

const filterCategory = ref('')
const filterStatus = ref('')
const keyword = ref('')
const showUploadModal = ref(false)
const showEditModal = ref(false)
const showDetailModal = ref(false)
const uploading = ref(false)
const fileList = ref<any[]>([])
const currentDoc = ref<KnowledgeDoc | null>(null)
const currentEditId = ref<number>()

// 进度相关状态
const uploadProgressRef = ref<InstanceType<typeof UploadProgress> | null>(null)
const reindexProgressRef = ref<InstanceType<typeof UploadProgress> | null>(null)
const uploadingDocId = ref<number | undefined>(undefined)
const reindexingDocId = ref<number | undefined>(undefined)
const showProgress = ref(false)
const showReindexModal = ref(false)
const showReindexProgress = ref(false)
const progressCompleted = ref(false)
const progressFailed = ref(false)
const reindexCompleted = ref(false)
const reindexFailed = ref(false)

const uploadForm = reactive<KnowledgeDocRequest>({
  title: '',
  description: '',
  category: '',
  tags: '',
})

const editForm = reactive<KnowledgeDocRequest>({
  title: '',
  description: '',
  category: '',
  tags: '',
})

const beforeUpload = (file: File) => {
  // 检查文件大小（50MB限制）
  const isLt50M = file.size / 1024 / 1024 < 50
  if (!isLt50M) {
    message.error('文件大小不能超过 50MB!')
    return false
  }
  
  // 检查文件是否为空
  if (file.size === 0) {
    message.error('文件不能为空!')
    return false
  }
  
  // 检查文件名是否有效
  if (!file.name || file.name.trim() === '') {
    message.error('文件名不能为空!')
    return false
  }
  
  // 检查文件扩展名
  const fileName = file.name.toLowerCase()
  const allowedExtensions = ['.pdf', '.txt', '.md', '.markdown', '.docx', '.doc', '.pptx', '.ppt', '.xlsx', '.xls']
  const hasValidExtension = allowedExtensions.some(ext => fileName.endsWith(ext))
  
  if (!hasValidExtension) {
    message.error('不支持的文件格式，请选择 PDF、Markdown、Word、PPT 或 Excel 文件')
    return false
  }
  
  // Auto set title from filename (remove extension)
  const name = file.name
  const dotIndex = name.lastIndexOf('.')
  if (dotIndex > 0) {
    uploadForm.title = name.substring(0, dotIndex)
  } else {
    uploadForm.title = name
  }

  // Auto set category to last item if available and not set
  if (!uploadForm.category && knowledgeStore.categories.length > 0) {
    uploadForm.category = knowledgeStore.categories[knowledgeStore.categories.length - 1].name
  }

  return false // 阻止自动上传
}

const handleUpload = async () => {
  if (fileList.value.length === 0) {
    message.error('请选择文件')
    return
  }

  if (!uploadForm.title) {
    message.error('请输入文档标题')
    return
  }
  
  if (!uploadForm.category) {
    message.error('请选择文档分类')
    return
  }

  uploading.value = true
  progressCompleted.value = false
  progressFailed.value = false
  
  try {
    const file = fileList.value[0].originFileObj || fileList.value[0]
    const doc = await knowledgeStore.uploadDocument(file, uploadForm)
    
    if (doc) {
      // 上传成功，显示进度
      uploadingDocId.value = doc.id
      showProgress.value = true
      
      // 订阅进度（组件会自动处理）
      if (uploadProgressRef.value) {
        uploadProgressRef.value.subscribeProgress(doc.id)
      }
    } else {
      // 上传失败，错误信息已经在拦截器中显示
      progressFailed.value = true
    }
  } catch (error) {
    // 错误信息已经在request.ts的拦截器中处理并显示
    progressFailed.value = true
  } finally {
    uploading.value = false
  }
}

// 进度完成处理
const handleUploadCompleted = (_docId: number) => {
  progressCompleted.value = true
  message.success('文档处理完成！')
  // 刷新文档列表
  fetchDocuments()
}

// 进度失败处理
const handleUploadFailed = (_docId: number, error: string) => {
  progressFailed.value = true
  message.error(`文档处理失败: ${error}`)
  // 刷新文档列表
  fetchDocuments()
}

// 上传完成后关闭所有相关弹窗
const handleUploadDone = () => {
  handleProgressClose()
  showUploadModal.value = false
}

// 进度关闭处理
const handleProgressClose = () => {
  showProgress.value = false
  uploadingDocId.value = undefined
  progressCompleted.value = false
  progressFailed.value = false
}

// 上传弹窗关闭处理
const handleUploadModalClose = () => {
  if (showProgress.value && !progressCompleted.value && !progressFailed.value) {
    // 如果正在处理中，提示用户
    Modal.confirm({
      title: '确认关闭',
      content: '文档正在处理中，关闭窗口不会中断处理，但您将无法查看进度。确定要关闭吗？',
      onOk: () => {
        resetUploadState()
        showUploadModal.value = false
      },
    })
    return
  }
  resetUploadState()
}

// 重置上传状态
const resetUploadState = () => {
  fileList.value = []
  Object.assign(uploadForm, {
    title: '',
    description: '',
    category: '',
    tags: '',
  })
  showProgress.value = false
  uploadingDocId.value = undefined
  progressCompleted.value = false
  progressFailed.value = false
  if (uploadProgressRef.value) {
    uploadProgressRef.value.reset()
  }
}

const handleViewDoc = async (doc: KnowledgeDoc) => {
  currentDoc.value = doc
  showDetailModal.value = true
}

const handleEditDoc = (doc: KnowledgeDoc) => {
  currentEditId.value = doc.id
  Object.assign(editForm, {
    title: doc.title,
    description: doc.description,
    category: doc.category,
    tags: doc.tags,
  })
  showEditModal.value = true
}

const handleUpdateDoc = async () => {
  if (!currentEditId.value) return

  await knowledgeStore.updateDocument(currentEditId.value, editForm)
  showEditModal.value = false
}

const handleDeleteDoc = (id: number) => {
  Modal.confirm({
    title: '确认彻底删除',
    content: '确定要彻底删除这个文档吗？此操作将删除文档文件、数据库记录和向量数据，删除后无法恢复！',
    okText: '彻底删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      await knowledgeStore.deleteDocument(id)
    },
  })
}

const handleReindex = (id: number) => {
  Modal.confirm({
    title: '重新索引',
    content: '确定要重新索引这个文档吗？',
    onOk: async () => {
      await knowledgeStore.reindexDocument(id)
      reindexingDocId.value = id
      showReindexModal.value = true
      showReindexProgress.value = true
      reindexCompleted.value = false
      reindexFailed.value = false
      
      // Wait for next tick to ensure modal is rendered
      nextTick(() => {
        if (reindexProgressRef.value) {
           reindexProgressRef.value.subscribeProgress(id)
        }
      })
    },
  })
}

const handleReindexClose = () => {
  showReindexModal.value = false
  showReindexProgress.value = false
  reindexingDocId.value = undefined
  reindexCompleted.value = false
  reindexFailed.value = false
}

const handleReindexCompleted = () => {
   showReindexProgress.value = true
   reindexCompleted.value = true
   fetchDocuments()
}

const handleReindexFailed = (_docId: number, msg: string) => {
   message.error(`索引重建失败: ${msg}`)
   showReindexProgress.value = true
   reindexFailed.value = true
}

const handleFilterChange = () => {
  knowledgeStore.setPage(1)
  fetchDocuments()
}

const handleSearch = () => {
  knowledgeStore.setPage(1)
  fetchDocuments()
}

const handlePageChange = () => {
  fetchDocuments()
}

const fetchDocuments = () => {
  knowledgeStore.fetchDocuments({
    category: filterCategory.value || undefined,
    status: filterStatus.value || undefined,
    keyword: keyword.value || undefined,
  })
}

const formatFileSize = (bytes: number) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const formatTime = (time: string) => {
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

const getStatusColor = (status: string) => {
  // Not used in new design but kept for fallback
  const colors: Record<string, string> = {
    ACTIVE: 'success',
    PROCESSING: 'processing',
    FAILED: 'error',
    INACTIVE: 'default',
  }
  return colors[status] || 'default'
}

const getStatusIcon = (status: string) => {
  const icons: Record<string, any> = {
    ACTIVE: CheckCircleFilled,
    PROCESSING: SyncOutlined,
    FAILED: CloseCircleFilled,
    INACTIVE: CheckCircleFilled, 
  }
  return icons[status] || CheckCircleFilled
}

const getStatusText = (status: string) => {
  const texts: Record<string, string> = {
    ACTIVE: '正常',
    PROCESSING: '处理中',
    FAILED: '失败',
    INACTIVE: '未激活',
  }
  return texts[status] || status
}

const getFileIcon = (fileType: string) => {
  const type = (fileType || '').toLowerCase()
  if (type.includes('pdf')) return FilePdfOutlined
  if (type.includes('doc')) return FileWordOutlined
  if (type.includes('md') || type.includes('markdown')) return FileMarkdownOutlined
  if (type.includes('txt')) return FileTextOutlined
  if (type.includes('ppt')) return FilePptOutlined
  if (type.includes('xls') || type.includes('excel')) return FileExcelOutlined
  return FileOutlined
}

const getFileIconColor = (fileType: string) => {
  const type = (fileType || '').toLowerCase()
  if (type.includes('pdf')) return '#ff4d4f'
  if (type.includes('doc')) return '#1890ff'
  if (type.includes('md') || type.includes('markdown')) return '#333'
  if (type.includes('txt')) return '#666'
  if (type.includes('ppt')) return '#fa8c16'
  if (type.includes('xls') || type.includes('excel')) return '#52c41a'
  return 'var(--primary-color)'
}

onMounted(() => {
  knowledgeStore.fetchDocuments()
  knowledgeStore.fetchCategories()
})
</script>

<style scoped>
.knowledge-view {
  min-height: calc(100vh - var(--header-height, 64px));
  position: relative;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

/* --- Immersive Background --- */
.immersive-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}

.bg-shape {
  position: absolute;
  filter: blur(80px);
  opacity: 0.6;
}

.shape-1 {
  top: -100px;
  right: -50px;
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.15) 0%, rgba(168, 85, 247, 0.05) 70%, transparent 100%);
  animation: float 20s infinite alternate ease-in-out;
}

.shape-2 {
  bottom: -150px;
  left: -100px;
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(236, 72, 153, 0.1) 0%, rgba(99, 102, 241, 0.05) 70%, transparent 100%);
  animation: float 25s infinite alternate-reverse ease-in-out;
}

@keyframes float {
  0% { transform: translate(0, 0) rotate(0deg); }
  100% { transform: translate(30px, 50px) rotate(10deg); }
}

/* --- Layout --- */
.content-wrapper {
  position: relative;
  z-index: 1;
  max-width: 1440px;
  margin: 0 auto;
  padding: 32px 40px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.knowledge-view {
  min-height: calc(100vh - var(--header-height, 64px));
  position: relative;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  background-color: transparent; /* Ensure allow parent bg to show */
}

/* --- Header Section --- */
.glass-header {
  margin-bottom: 40px;
  animation: fadeSlideDown 0.6s ease-out;
  position: relative;
  z-index: 10;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 32px;
}

.title-group .page-title {
  font-size: 36px;
  font-weight: 800;
  color: var(--text-primary);
  margin-bottom: 8px;
  letter-spacing: -0.02em;
  background: linear-gradient(135deg, var(--text-primary) 0%, #4338ca 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.title-group .page-subtitle {
  font-size: 16px;
  color: var(--text-secondary);
  font-weight: 400;
}

.action-btn-primary {
  height: 48px;
  border-radius: var(--radius-full);
  padding: 0 32px;
  font-size: 16px;
  font-weight: 600;
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.25), 0 0 0 1px rgba(255, 255, 255, 0.1) inset;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.action-btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.35), 0 0 0 1px rgba(255, 255, 255, 0.2) inset;
  filter: brightness(1.05);
}

/* --- Toolbar --- */
.toolbar-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(12px);
  padding: 8px 12px;
  border-radius: var(--radius-xl);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: var(--shadow-sm);
}

.search-box {
  display: flex;
  align-items: center;
  padding: 0 16px;
  flex: 1;
  max-width: 400px;
}

.search-icon {
  font-size: 18px;
  color: var(--text-tertiary);
  margin-right: 12px;
}

.transparent-input {
  width: 100%;
  border: none;
  background: transparent;
  font-size: 15px;
  color: var(--text-primary);
  outline: none;
  padding: 8px 0;
}

.transparent-input::placeholder {
  color: #94a3b8;
}

.filter-actions {
  display: flex;
  gap: 12px;
}

.glass-select {
  min-width: 140px;
}

:deep(.glass-select .ant-select-selector),
:deep(.ant-select:not(.ant-select-disabled):hover .ant-select-selector) {
  background-color: transparent !important;
  border: none !important;
  box-shadow: none !important;
  font-weight: 500;
  color: var(--text-secondary);
}

/* --- Document Grid --- */
.scroll-area {
  flex: 1;
  /* overflow-y: auto; */
  /* padding-bottom: 40px; */
}

.grid-layout {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 32px;
  padding: 4px; /* Space for shadows */
  animation: fadeSlideUp 0.6s ease-out 0.1s backwards;
}

.knowledge-card {
  position: relative;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(8px);
  border-radius: var(--radius-lg);
  border: 1px solid rgba(255, 255, 255, 0.6);
  padding: 24px;
  display: flex;
  flex-direction: column;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 
    0 4px 6px -1px rgba(0, 0, 0, 0.02),
    0 2px 4px -1px rgba(0, 0, 0, 0.02);
  cursor: pointer;
  overflow: hidden;
}

.knowledge-card:hover {
  transform: translateY(-2px);
  background: #ffffff;
  border-color: var(--primary-color);
  box-shadow: 
    0 10px 15px -3px rgba(99, 102, 241, 0.1), 
    0 4px 6px -2px rgba(99, 102, 241, 0.05);
}

/* Card Header */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.icon-wrapper {
  position: relative;
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  border-radius: 16px;
  overflow: hidden;
}

.icon-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.more-btn {
  color: var(--text-tertiary);
  transition: color 0.2s;
}

.more-btn:hover {
  color: var(--text-primary);
  background: var(--bg-secondary);
}

/* Card Body */
.card-body {
  flex: 1;
  margin-bottom: 20px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 20px;
  margin-bottom: 12px;
  background: var(--bg-secondary);
  color: var(--text-secondary);
}

.status-badge.active { background: rgba(16, 185, 129, 0.1); color: #10b981; }
.status-badge.processing { background: rgba(59, 130, 246, 0.1); color: #3b82f6; }
.status-badge.failed { background: rgba(239, 68, 68, 0.1); color: #ef4444; }

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: currentColor;
  margin-right: 6px;
}

.doc-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-snippet {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  height: 44px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

/* Card Footer */
.card-footer {
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: var(--text-tertiary);
}

.meta-info {
  display: flex;
  align-items: center;
}

.meta-tag {
  font-weight: 600;
  color: var(--text-secondary);
}

.meta-dot {
  margin: 0 6px;
  opacity: 0.5;
}

.hover-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  box-shadow: inset 0 0 0 2px var(--primary-color);
  border-radius: var(--radius-lg);
  opacity: 0;
  transition: opacity 0.3s;
  pointer-events: none;
}

.knowledge-card:active .hover-overlay {
  opacity: 0.1;
}

/* Empty State */
.empty-placeholder {
  text-align: center;
  padding: 80px 0;
  animation: fadeSlideUp 0.6s ease-out;
}

.empty-illustration {
  font-size: 64px;
  color: #e2e8f0;
  margin-bottom: 24px;
}

.empty-placeholder h3 {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.empty-placeholder p {
  color: var(--text-tertiary);
  margin-bottom: 32px;
}

.pagination-bar {
  display: flex;
  justify-content: center;
  margin-top: 48px;
  position: relative;
  z-index: 2;
}

/* Animations */
@keyframes fadeSlideDown {
  from { opacity: 0; transform: translateY(-20px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes fadeSlideUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Responsive */
@media (max-width: 1024px) {
  .content-wrapper { padding: 24px; }
  .grid-layout { grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); }
}

@media (max-width: 768px) {
  .header-content { flex-direction: column; align-items: flex-start; gap: 16px; }
  .toolbar-container { flex-direction: column; width: 100%; gap: 16px; }
  .search-box { width: 100%; padding: 0; }
  .filter-actions { width: 100%; justify-content: space-between; }
  .glass-header { margin-bottom: 24px; }
}

/* Modal Styling Overrides (Global scope needed sometimes, but scoped works if not ported to body) */
:deep(.elegant-modal-wrap) .ant-modal-content {
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-xl);
  padding: 32px;
}

:deep(.elegant-upload) .ant-upload {
  width: 100%;
  border: 2px dashed #e2e8f0;
  border-radius: var(--radius-lg);
  padding: 32px 0;
  background: #fafafa;
  transition: all 0.3s;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

:deep(.elegant-upload) .ant-upload:hover {
  border-color: var(--primary-color);
  background: rgba(99, 102, 241, 0.02);
}

.upload-trigger-btn {
  margin-bottom: 12px;
}

.upload-form-container,
.upload-progress-view {
  min-height: auto;
  display: flex;
  flex-direction: column;
}

.upload-progress-view {
    justify-content: flex-start;
    padding: 24px;
}

.upload-hint {
  font-size: 12px;
  color: var(--text-tertiary);
}

.elegant-upload-clean {
  width: 100%;
}
:deep(.elegant-upload-clean .ant-upload-list-item) {
  border-radius: var(--radius-md);
  margin-top: 8px;
}

/* --- Progress View --- */
.upload-progress-view {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 10px 20px 20px; /* Reduced top/bottom padding */
  animation: fadeSlideUp 0.4s ease-out;
}

.progress-illustration {
  position: relative;
  width: 80px; /* Reduced from 100px */
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px; /* Reduced from 24px */
}

.pulse-ring {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: var(--primary-color);
  opacity: 0.1;
  animation: pulse 2s infinite;
}

.upload-icon-large {
  font-size: 40px; /* Reduced from 48px */
  color: var(--primary-color);
  z-index: 1;
}

.progress-title {
  font-size: 18px; /* Reduced from 20px */
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 24px; /* Reduced from 32px */
}

.embedded-progress {
  width: 100%;
  max-width: 400px;
}

.progress-actions {
  margin-top: 24px; /* Reduced from 32px */
}

.vector-count {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-tertiary);
}

.vector-icon {
  font-size: 14px;
  color: #a855f7; /* Purple accent */
}

/* Compact version for reindex modal */
.upload-progress-view.compact {
  padding: 0;
}
.progress-illustration.small {
  width: 60px;
  height: 60px;
}

@keyframes pulse {
  0% { transform: scale(0.9); opacity: 0.1; }
  50% { transform: scale(1.1); opacity: 0.2; }
  100% { transform: scale(0.9); opacity: 0.1; }
}

/* --- New Status Styles --- */
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: var(--radius-full);
  font-size: 13px;
  font-weight: 600;
  background: #f1f5f9;
  color: var(--text-secondary);
  margin-bottom: 12px;
  position: relative;
  overflow: hidden;
}

.status-pill.active { background: #ecfdf5; color: #059669; }
.status-pill.processing { background: #eff6ff; color: #3b82f6; }
.status-pill.failed { background: #fef2f2; color: #dc2626; }

.processing-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 2px;
  background: rgba(59, 130, 246, 0.2);
}

.bar-inner {
  height: 100%;
  background: #3b82f6;
  width: 50%;
  animation: loading-bar 1.5s infinite linear;
}

@keyframes loading-bar {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(200%); }
}

</style>
