<template>
  <div class="knowledge-container">
    <div class="knowledge-header">
      <div class="header-left">
        <h2 class="page-title">知识库管理</h2>
        <a-space>
          <a-select
            v-model:value="filterCategory"
            placeholder="选择分类"
            style="width: 150px"
            allowClear
            @change="handleFilterChange"
          >
            <a-select-option value="">全部分类</a-select-option>
            <a-select-option
              v-for="cat in knowledgeStore.categories"
              :key="cat.id"
              :value="cat.name"
            >
              {{ cat.name }} ({{ cat.docCount }})
            </a-select-option>
          </a-select>

          <a-select
            v-model:value="filterStatus"
            placeholder="文档状态"
            style="width: 120px"
            allowClear
            @change="handleFilterChange"
          >
            <a-select-option value="">全部状态</a-select-option>
            <a-select-option value="ACTIVE">正常</a-select-option>
            <a-select-option value="PROCESSING">处理中</a-select-option>
            <a-select-option value="FAILED">失败</a-select-option>
          </a-select>

          <a-input-search
            v-model:value="keyword"
            placeholder="搜索文档..."
            style="width: 250px"
            @search="handleSearch"
          />
        </a-space>
      </div>

      <a-button type="primary" size="large" @click="showUploadModal = true">
        <UploadOutlined /> 上传文档
      </a-button>
    </div>

    <a-spin :spinning="knowledgeStore.isLoading">
      <div class="documents-grid">
        <div
          v-for="doc in knowledgeStore.documents"
          :key="doc.id"
          class="document-card"
        >
          <div class="document-header">
            <FileOutlined class="document-icon" />
            <a-dropdown :trigger="['click']">
              <MoreOutlined class="document-more" />
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

          <div class="document-body">
            <h3 class="document-title">{{ doc.title }}</h3>
            <p class="document-desc">{{ doc.description || '暂无描述' }}</p>

            <div class="document-meta">
              <a-tag :color="getStatusColor(doc.status)">{{ getStatusText(doc.status) }}</a-tag>
              <a-tag v-if="doc.category" color="blue">{{ doc.category }}</a-tag>
              <span class="document-size">{{ formatFileSize(doc.fileSize) }}</span>
            </div>

            <div class="document-stats">
              <div class="stat-item">
                <span class="stat-label">向量数</span>
                <span class="stat-value">{{ doc.vectorCount || 0 }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">文件类型</span>
                <span class="stat-value">{{ doc.fileType.toUpperCase() }}</span>
              </div>
            </div>

            <div class="document-footer">
              <span class="document-time">{{ formatTime(doc.createdAt) }}</span>
            </div>
          </div>
        </div>

        <a-empty
          v-if="knowledgeStore.documents.length === 0 && !knowledgeStore.isLoading"
          description="暂无文档"
          :image="Empty.PRESENTED_IMAGE_SIMPLE"
        />
      </div>

      <div v-if="knowledgeStore.total > 0" class="pagination-container">
        <a-pagination
          v-model:current="knowledgeStore.page"
          v-model:pageSize="knowledgeStore.size"
          :total="knowledgeStore.total"
          show-size-changer
          show-quick-jumper
          :show-total="(total) => `共 ${total} 个文档`"
          @change="handlePageChange"
        />
      </div>
    </a-spin>

    <!-- 上传文档弹窗 -->
    <a-modal
      v-model:open="showUploadModal"
      title="上传知识库文档"
      :confirm-loading="uploading"
      @ok="handleUpload"
      width="600px"
    >
      <a-form :model="uploadForm" layout="vertical">
        <a-form-item label="文件" required>
          <a-upload
            v-model:file-list="fileList"
            :before-upload="beforeUpload"
            :max-count="1"
            accept=".pdf,.txt,.md,.docx,.doc,.ppt,.pptx"
          >
            <a-button>
              <UploadOutlined /> 选择文件
            </a-button>
            <div style="margin-top: 8px; color: #999; font-size: 12px">
              支持格式: PDF, TXT, MD, DOCX, DOC, PPT, PPTX (最大50MB)
            </div>
          </a-upload>
        </a-form-item>

        <a-form-item label="文档标题" required>
          <a-input v-model:value="uploadForm.title" placeholder="请输入文档标题" />
        </a-form-item>

        <a-form-item label="文档描述">
          <a-textarea
            v-model:value="uploadForm.description"
            :rows="3"
            placeholder="请输入文档描述（可选）"
          />
        </a-form-item>

        <a-form-item label="分类">
          <a-select
            v-model:value="uploadForm.category"
            placeholder="选择分类（可选）"
            allowClear
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
          />
        </a-form-item>
      </a-form>
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
} from '@ant-design/icons-vue'

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
  try {
    const file = fileList.value[0].originFileObj || fileList.value[0]
    await knowledgeStore.uploadDocument(file, uploadForm)
    
    showUploadModal.value = false
    fileList.value = []
    Object.assign(uploadForm, {
      title: '',
      description: '',
      category: '',
      tags: '',
    })
  } finally {
    uploading.value = false
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

onMounted(() => {
  knowledgeStore.fetchDocuments()
  knowledgeStore.fetchCategories()
})
</script>

<style scoped>
.knowledge-container {
  background: white;
  border-radius: 12px;
  padding: 24px;
  min-height: calc(100vh - 160px);
}

.knowledge-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  margin: 0;
}

.documents-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
  margin-bottom: 24px;
}

.document-card {
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  padding: 20px;
  transition: all 0.3s;
  background: white;
}

.document-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.document-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.document-icon {
  font-size: 28px;
  color: #1890ff;
}

.document-more {
  font-size: 18px;
  color: #999;
  cursor: pointer;
  padding: 4px;
}

.document-more:hover {
  color: #1890ff;
}

.document-body {
  flex: 1;
}

.document-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 8px;
  color: #1a1a1a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-desc {
  font-size: 13px;
  color: #666;
  margin: 0 0 12px;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.document-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
  align-items: center;
}

.document-size {
  font-size: 12px;
  color: #999;
}

.document-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
  margin-bottom: 12px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  font-size: 12px;
  color: #999;
}

.stat-value {
  font-size: 14px;
  font-weight: 500;
  color: #1a1a1a;
}

.document-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.document-time {
  font-size: 12px;
  color: #999;
}

.pagination-container {
  display: flex;
  justify-content: center;
  padding-top: 24px;
  border-top: 1px solid #f0f0f0;
}

@media (max-width: 768px) {
  .documents-grid {
    grid-template-columns: 1fr;
  }

  .knowledge-header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-left {
    flex-direction: column;
  }
}
</style>
