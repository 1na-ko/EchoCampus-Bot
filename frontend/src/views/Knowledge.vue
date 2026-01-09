<template>
  <div class="knowledge-view">
    <div class="content-container">
      <!-- Background Elements -->
      <div class="bg-decoration"></div>

      <!-- Header Section -->
      <div class="page-header">
        <div class="header-main">
          <div class="title-section">
            <h1 class="main-title">知识库</h1>
            <p class="sub-title">管理您的文档资产与知识向量</p>
          </div>
          <a-button type="primary" size="large" class="upload-btn" @click="showUploadModal = true">
            <template #icon><UploadOutlined /></template>
            上传文档
          </a-button>
        </div>

        <!-- Filter Bar -->
        <div class="filter-bar">
          <div class="search-wrapper">
             <a-input
              v-model:value="keyword"
              placeholder="搜索文档..."
              class="custom-input search-input"
              @pressEnter="handleSearch"
            >
              <template #prefix><SearchOutlined style="color: #bfbfbf" /></template>
            </a-input>
          </div>
          
          <div class="filters-group">
            <a-select
              v-model:value="filterCategory"
              placeholder="全部分类"
              class="custom-select"
              :bordered="false"
              allowClear
              @change="handleFilterChange"
            >
              <a-select-option value="">全部分类</a-select-option>
              <a-select-option
                v-for="cat in knowledgeStore.categories"
                :key="cat.id"
                :value="cat.name"
              >
                {{ cat.name }} <span style="color: #999; font-size: 12px">({{ cat.docCount }})</span>
              </a-select-option>
            </a-select>

            <a-select
              v-model:value="filterStatus"
              placeholder="全部状态"
              class="custom-select"
              style="width: 140px"
              :bordered="false"
              allowClear
              @change="handleFilterChange"
            >
              <a-select-option value="">全部状态</a-select-option>
              <a-select-option value="ACTIVE">正常</a-select-option>
              <a-select-option value="PROCESSING">处理中</a-select-option>
              <a-select-option value="FAILED">失败</a-select-option>
            </a-select>
          </div>
        </div>
      </div>

      <!-- Content Grid -->
      <a-spin :spinning="knowledgeStore.isLoading">
        <div class="documents-grid" v-if="knowledgeStore.documents.length > 0">
          <div
            v-for="doc in knowledgeStore.documents"
            :key="doc.id"
            class="document-card"
          >
            <div class="card-status-indicator" :class="doc.status.toLowerCase()"></div>
            
            <div class="card-top">
              <div class="file-icon-box" :style="{ background: getFileIconColor(doc.fileType) + '15', color: getFileIconColor(doc.fileType) }">
                <component :is="getFileIcon(doc.fileType)" />
              </div>
              <div class="card-menu">
                <a-dropdown :trigger="['click']">
                  <a-button type="text" shape="circle" size="small">
                    <MoreOutlined />
                  </a-button>
                  <template #overlay>
                    <a-menu>
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

            <div class="card-content">
              <h3 class="doc-title" :title="doc.title">{{ doc.title }}</h3>
              <p class="doc-desc">{{ doc.description || '暂无描述' }}</p>
              
              <div class="doc-meta">
                 <span class="meta-item"><span class="label">大小: </span>{{ formatFileSize(doc.fileSize) }}</span>
                 <span class="meta-item category" v-if="doc.category">{{ doc.category }}</span>
              </div>
            </div>

            <div class="card-footer">
              <div class="footer-stats">
                 <div class="stat">
                    <span class="val">{{ doc.vectorCount || 0 }}</span>
                    <span class="lbl">向量</span>
                 </div>
                 <div class="divider"></div>
                 <div class="stat">
                    <span class="val">{{ doc.fileType.toUpperCase() }}</span>
                    <span class="lbl">类型</span>
                 </div>
              </div>
              <div class="footer-time">{{ formatTime(doc.createdAt).split(' ')[0] }}</div>
            </div>
          </div>
        </div>

        <div v-else-if="!knowledgeStore.isLoading" class="empty-state">
           <div class="empty-icon-bg">
             <FileOutlined />
           </div>
           <h3>暂无文档</h3>
           <p>您可以上传文档来构建专属于您的知识库</p>
           <a-button type="primary" @click="showUploadModal = true">立即上传</a-button>
        </div>

        <div v-if="knowledgeStore.total > 0" class="pagination-wrapper">
          <a-pagination
            v-model:current="knowledgeStore.page"
            v-model:pageSize="knowledgeStore.size"
            :total="knowledgeStore.total"
            show-size-changer
            :show-total="(total) => `共 ${total} 个文档`"
            @change="handlePageChange"
          />
        </div>
      </a-spin>
    </div>

    <!-- 上传文档弹窗 -->
    <a-modal
      v-model:open="showUploadModal"
      title="上传知识库文档"
      :confirm-loading="uploading"
      :ok-button-props="{ disabled: showProgress }"
      :cancel-button-props="{ disabled: showProgress && !progressCompleted }"
      @ok="handleUpload"
      @cancel="handleUploadModalClose"
      width="650px"
      :maskClosable="!showProgress"
      :closable="!showProgress || progressCompleted || progressFailed"
    >
      <a-form :model="uploadForm" layout="vertical">
        <a-form-item label="文件" required>
          <a-upload
            v-model:file-list="fileList"
            :before-upload="beforeUpload"
            :max-count="1"
            :disabled="showProgress"
            accept=".pdf,.txt,.md,.docx,.doc,.ppt,.pptx"
          >
            <a-button :disabled="showProgress">
              <UploadOutlined /> 选择文件
            </a-button>
            <div style="margin-top: 8px; color: #999; font-size: 12px">
              支持格式: PDF, TXT, MD, DOCX, DOC, PPT, PPTX (最大50MB)
            </div>
          </a-upload>
        </a-form-item>

        <a-form-item label="文档标题" required>
          <a-input 
            v-model:value="uploadForm.title" 
            placeholder="请输入文档标题"
            :disabled="showProgress"
          />
        </a-form-item>

        <a-form-item label="文档描述">
          <a-textarea
            v-model:value="uploadForm.description"
            :rows="3"
            placeholder="请输入文档描述（可选）"
            :disabled="showProgress"
          />
        </a-form-item>

        <a-form-item label="分类">
          <a-select
            v-model:value="uploadForm.category"
            placeholder="选择分类（可选）"
            allowClear
            :disabled="showProgress"
          >
            <a-select-option
              v-for="cat in knowledgeStore.categories"
              :key="cat.id"
              :value="cat.name"
            >
              {{ cat.name }}
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="标签">
          <a-input
            v-model:value="uploadForm.tags"
            placeholder="多个标签用逗号分隔（可选）"
            :disabled="showProgress"
          />
        </a-form-item>
      </a-form>

      <!-- 进度显示组件 -->
      <UploadProgress
        ref="uploadProgressRef"
        :doc-id="uploadingDocId"
        :visible="showProgress"
        @close="handleProgressClose"
        @completed="handleUploadCompleted"
        @failed="handleUploadFailed"
      />
    </a-modal>

    <!-- 编辑文档弹窗 -->
    <a-modal
      v-model:open="showEditModal"
      title="编辑文档信息"
      @ok="handleUpdateDoc"
      width="600px"
    >
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="文档标题" required>
          <a-input v-model:value="editForm.title" />
        </a-form-item>

        <a-form-item label="文档描述">
          <a-textarea v-model:value="editForm.description" :rows="3" />
        </a-form-item>

        <a-form-item label="分类">
          <a-select v-model:value="editForm.category" allowClear>
            <a-select-option
              v-for="cat in knowledgeStore.categories"
              :key="cat.id"
              :value="cat.name"
            >
              {{ cat.name }}
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="标签">
          <a-input v-model:value="editForm.tags" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 文档详情弹窗 -->
    <a-modal
      v-model:open="showDetailModal"
      title="文档详情"
      :footer="null"
      width="700px"
    >
      <a-descriptions v-if="currentDoc" bordered :column="1">
        <a-descriptions-item label="标题">{{ currentDoc.title }}</a-descriptions-item>
        <a-descriptions-item label="描述">
          {{ currentDoc.description || '暂无' }}
        </a-descriptions-item>
        <a-descriptions-item label="文件名">{{ currentDoc.fileName }}</a-descriptions-item>
        <a-descriptions-item label="文件大小">
          {{ formatFileSize(currentDoc.fileSize) }}
        </a-descriptions-item>
        <a-descriptions-item label="文件类型">
          {{ currentDoc.fileType.toUpperCase() }}
        </a-descriptions-item>
        <a-descriptions-item label="分类">
          {{ currentDoc.category || '未分类' }}
        </a-descriptions-item>
        <a-descriptions-item label="标签">
          {{ currentDoc.tags || '无' }}
        </a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="getStatusColor(currentDoc.status)">
            {{ getStatusText(currentDoc.status) }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="向量数量">
          {{ currentDoc.vectorCount || 0 }}
        </a-descriptions-item>
        <a-descriptions-item label="处理状态">
          {{ currentDoc.processStatus || '未知' }}
        </a-descriptions-item>
        <a-descriptions-item label="上传时间">
          {{ dayjs(currentDoc.createdAt).format('YYYY-MM-DD HH:mm:ss') }}
        </a-descriptions-item>
        <a-descriptions-item label="更新时间">
          {{ dayjs(currentDoc.updatedAt).format('YYYY-MM-DD HH:mm:ss') }}
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { Modal, Empty, message } from 'ant-design-vue'
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
  SearchOutlined,
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
const uploadingDocId = ref<number | undefined>(undefined)
const showProgress = ref(false)
const progressCompleted = ref(false)
const progressFailed = ref(false)

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
  const isLt50M = file.size / 1024 / 1024 < 50
  if (!isLt50M) {
    message.error('文件大小不能超过 50MB!')
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
    }
  } catch (error) {
    message.error('上传失败')
    progressFailed.value = true
  } finally {
    uploading.value = false
  }
}

// 进度完成处理
const handleUploadCompleted = (docId: number) => {
  progressCompleted.value = true
  message.success('文档处理完成！')
  // 刷新文档列表
  fetchDocuments()
}

// 进度失败处理
const handleUploadFailed = (docId: number, error: string) => {
  progressFailed.value = true
  message.error(`文档处理失败: ${error}`)
  // 刷新文档列表
  fetchDocuments()
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
    },
  })
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
  const colors: Record<string, string> = {
    ACTIVE: 'success',
    PROCESSING: 'processing',
    FAILED: 'error',
    INACTIVE: 'default',
  }
  return colors[status] || 'default'
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
  return FileOutlined
}

const getFileIconColor = (fileType: string) => {
  const type = (fileType || '').toLowerCase()
  if (type.includes('pdf')) return '#ff4d4f'
  if (type.includes('doc')) return '#1890ff'
  if (type.includes('md') || type.includes('markdown')) return '#333'
  if (type.includes('txt')) return '#666'
  if (type.includes('ppt')) return '#fa8c16'
  return 'var(--primary-color)'
}

onMounted(() => {
  knowledgeStore.fetchDocuments()
  knowledgeStore.fetchCategories()
})
</script>

<style scoped>
.knowledge-view {
  min-height: calc(100vh - 84px);
  padding: 0;
  position: relative;
  /* border-radius: var(--radius-lg); */
  overflow: visible;
}

.content-container {
  position: relative;
  z-index: 1;
}

/* Background Decoration */
.bg-decoration {
  position: absolute;
  top: -40px;
  right: -40px;
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.05) 0%, transparent 70%);
  pointer-events: none;
  z-index: 0;
}

/* Page Header */
.page-header {
  margin-bottom: 32px;
}

.header-main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.main-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 4px;
  letter-spacing: -0.5px;
}

.sub-title {
  color: var(--text-tertiary);
  font-size: 14px;
}

.upload-btn {
  height: 44px;
  border-radius: var(--radius-full);
  padding: 0 24px;
  font-weight: 500;
  background: var(--gradient-primary);
  border: none;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  transition: all 0.3s ease;
}

.upload-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(99, 102, 241, 0.4);
}

/* Filter Bar */
.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  background: var(--surface-color);
  padding: 8px;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-light);
}

.search-wrapper {
  flex: 1;
  min-width: 240px;
}

.custom-input :deep(.ant-input),
.custom-input :deep(.ant-input-affix-wrapper) {
  border: none;
  box-shadow: none !important;
  background: transparent;
}

.filters-group {
  display: flex;
  gap: 12px;
}

.custom-select {
  min-width: 140px;
}

/* Grid */
.documents-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
  padding-bottom: 40px;
}

.document-card {
  position: relative;
  background: var(--surface-color);
  border-radius: var(--radius-lg);
  padding: 20px;
  border: 1px solid var(--border-color);
  transition: all var(--transition-normal);
  cursor: pointer;
  overflow: hidden;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.document-card:hover {
  transform: translateY(-5px);
  box-shadow: var(--shadow-lg);
  border-color: transparent;
}

.card-status-indicator {
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: #d9d9d9;
  transition: background 0.3s;
}

.card-status-indicator.active { background: #52c41a; }
.card-status-indicator.processing { background: #1890ff; }
.card-status-indicator.failed { background: #ff4d4f; }

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  padding-left: 12px;
}

.file-icon-box {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.card-menu .more-btn {
  color: var(--text-tertiary);
}
.card-menu .more-btn:hover {
  color: var(--text-primary);
  background: var(--bg-secondary);
}

.card-content {
  flex: 1;
  padding-left: 12px;
  margin-bottom: 16px;
}

.doc-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.doc-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  height: 40px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  margin-bottom: 12px;
}

.doc-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.meta-tag.category {
  color: var(--primary-color);
  background: rgba(99, 102, 241, 0.1);
  padding: 2px 8px;
  border-radius: 4px;
}

.card-footer {
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-left: 12px;
}

.footer-stats {
  display: flex;
  align-items: center;
  gap: 12px;
}

.footer-stats .stat {
  display: flex;
  flex-direction: column;
}

.footer-stats .val {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1;
}

.footer-stats .lbl {
  font-size: 10px;
  color: var(--text-tertiary);
  margin-top: 2px;
}

.footer-stats .divider {
  width: 1px;
  height: 20px;
  background: var(--border-light);
}

.footer-time {
  font-size: 12px;
  color: var(--text-tertiary);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  text-align: center;
}

.empty-icon-bg {
  width: 80px;
  height: 80px;
  background: var(--bg-secondary);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  color: var(--text-tertiary);
  margin-bottom: 16px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

@media (max-width: 768px) {
  .header-main {
    flex-direction: column;
    gap: 16px;
  }
  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
