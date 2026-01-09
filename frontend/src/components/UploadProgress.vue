<template>
  <div class="upload-progress-container" v-if="visible">
    <div class="progress-header">
      <div class="progress-title">
        <LoadingOutlined v-if="!isCompleted && !isFailed" spin />
        <CheckCircleOutlined v-else-if="isCompleted" class="success-icon" />
        <CloseCircleOutlined v-else class="error-icon" />
        <span>{{ progressTitle }}</span>
      </div>
      <a-button 
        v-if="(isCompleted || isFailed) && props.showHeaderClose" 
        type="text" 
        size="small" 
        @click="handleClose"
      >
        关闭
      </a-button>
    </div>

    <div class="progress-content">
      <!-- 总体进度 -->
      <div class="total-progress">
        <div class="progress-label">
          <span>总体进度</span>
          <span class="progress-percent">{{ progress?.totalProgress || 0 }}%</span>
        </div>
        <a-progress 
          :percent="progress?.totalProgress || 0" 
          :status="progressStatus"
          :stroke-color="progressColor"
        />
      </div>

      <!-- 阶段进度列表 -->
      <div class="stages-list">
        <div 
          v-for="stage in stages" 
          :key="stage.key"
          class="stage-item"
          :class="{ 
            active: currentStage === stage.key,
            completed: isStageCompleted(stage.key),
            failed: isFailed && currentStage === stage.key
          }"
        >
          <div class="stage-icon">
            <CheckCircleFilled v-if="isStageCompleted(stage.key)" class="completed-icon" />
            <CloseCircleFilled v-else-if="isFailed && currentStage === stage.key" class="failed-icon" />
            <LoadingOutlined v-else-if="currentStage === stage.key" spin />
            <div v-else class="pending-icon">{{ stage.index }}</div>
          </div>
          <div class="stage-info">
            <div class="stage-name">{{ stage.name }}</div>
            <div class="stage-desc" v-if="currentStage === stage.key">
              {{ progress?.message }}
              <span v-if="progress?.details" class="stage-details">
                {{ progress.details }}
              </span>
            </div>
            <a-progress 
              v-if="currentStage === stage.key && !isCompleted && !isFailed"
              :percent="progress?.progress || 0"
              size="small"
              :show-info="false"
              :stroke-color="progressColor"
            />
          </div>
        </div>
      </div>

      <!-- 错误信息 -->
      <div v-if="isFailed && progress?.errorMessage" class="error-message">
        <ExclamationCircleOutlined />
        <span>{{ progress.errorMessage }}</span>
      </div>

      <!-- 完成信息 -->
      <div v-if="isCompleted" class="success-message">
        <CheckCircleOutlined />
        <span>{{ progress?.details || '文档处理完成！' }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch, onUnmounted, ref } from 'vue'
import {
  LoadingOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  CheckCircleFilled,
  CloseCircleFilled,
  ExclamationCircleOutlined,
} from '@ant-design/icons-vue'
import { knowledgeApi } from '@/api'
import type { DocumentProgress, DocumentProcessStage } from '@/types'

const props = withDefaults(defineProps<{
  docId?: number
  visible: boolean
  showHeaderClose?: boolean
}>(), {
  showHeaderClose: true
})

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'completed', docId: number): void
  (e: 'failed', docId: number, error: string): void
}>()

// 进度数据
const progress = ref<DocumentProgress | null>(null)
const eventSource = ref<EventSource | null>(null)

// 处理阶段定义
const stages = [
  { key: 'UPLOADING', name: '文件上传', index: 1 },
  { key: 'PARSING', name: '文档解析', index: 2 },
  { key: 'CHUNKING', name: '文本切块', index: 3 },
  { key: 'EMBEDDING', name: '向量化处理', index: 4 },
  { key: 'STORING', name: '数据存储', index: 5 },
]

// 阶段顺序映射
const stageOrder: Record<string, number> = {
  'UPLOADING': 1,
  'PARSING': 2,
  'CHUNKING': 3,
  'EMBEDDING': 4,
  'STORING': 5,
  'COMPLETED': 6,
  'FAILED': -1,
  'PENDING': 0,
  'PROCESSING': 1,
}

// 计算属性
const currentStage = computed(() => progress.value?.stage || 'PENDING')
const isCompleted = computed(() => progress.value?.completed === true)
const isFailed = computed(() => progress.value?.failed === true)

const progressTitle = computed(() => {
  if (isCompleted.value) return '处理完成'
  if (isFailed.value) return '处理失败'
  return progress.value?.stageName || '准备处理...'
})

const progressStatus = computed(() => {
  if (isCompleted.value) return 'success'
  if (isFailed.value) return 'exception'
  return 'active'
})

const progressColor = computed(() => {
  if (isCompleted.value) return '#52c41a'
  if (isFailed.value) return '#ff4d4f'
  return '#1890ff'
})

// 判断阶段是否完成
const isStageCompleted = (stageKey: string) => {
  if (isCompleted.value) return true
  const currentOrder = stageOrder[currentStage.value] || 0
  const stageOrderNum = stageOrder[stageKey] || 0
  return currentOrder > stageOrderNum
}

// 订阅进度
const subscribeProgress = (docId: number) => {
  // 先清理旧的连接
  if (eventSource.value) {
    eventSource.value.close()
    eventSource.value = null
  }

  // 发送初始上传进度
  progress.value = {
    docId,
    stage: 'UPLOADING',
    stageName: '文件上传',
    progress: 100,
    totalProgress: 10,
    message: '文件上传完成，开始处理...',
    completed: false,
    failed: false,
  }

  // 建立SSE连接
  eventSource.value = knowledgeApi.subscribeProgress(docId, {
    onProgress: (data) => {
      progress.value = data
    },
    onError: (error) => {
      console.error('进度订阅错误:', error)
      // 如果连接失败，尝试获取当前进度
      fetchCurrentProgress(docId)
    },
    onComplete: () => {
      if (progress.value?.completed) {
        emit('completed', docId)
      } else if (progress.value?.failed) {
        emit('failed', docId, progress.value.errorMessage || '未知错误')
      }
    },
  })
}

// 获取当前进度（作为备选方案）
const fetchCurrentProgress = async (docId: number) => {
  try {
    const res = await knowledgeApi.getCurrentProgress(docId)
    progress.value = res.data
  } catch (error) {
    console.error('获取进度失败:', error)
  }
}

// 关闭
const handleClose = () => {
  if (eventSource.value) {
    eventSource.value.close()
    eventSource.value = null
  }
  progress.value = null
  emit('close')
}

// 监听docId变化
watch(() => props.docId, (newDocId) => {
  if (newDocId && props.visible) {
    subscribeProgress(newDocId)
  }
}, { immediate: true })

// 监听visible变化
watch(() => props.visible, (newVisible) => {
  if (!newVisible && eventSource.value) {
    eventSource.value.close()
    eventSource.value = null
  }
})

// 组件卸载时清理
onUnmounted(() => {
  if (eventSource.value) {
    eventSource.value.close()
    eventSource.value = null
  }
})

// 暴露方法给父组件
defineExpose({
  subscribeProgress,
  reset: () => {
    progress.value = null
    if (eventSource.value) {
      eventSource.value.close()
      eventSource.value = null
    }
  },
})
</script>

<style scoped>
.upload-progress-container {
  background: #fafafa;
  border-radius: 8px;
  padding: 16px;
  margin-top: 16px;
  border: 1px solid #f0f0f0;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.progress-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  font-size: 14px;
}

.success-icon {
  color: #52c41a;
}

.error-icon {
  color: #ff4d4f;
}

.progress-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.total-progress {
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.progress-label {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  color: #666;
}

.progress-percent {
  font-weight: 500;
  color: #1890ff;
}

.stages-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.stage-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 8px 12px;
  border-radius: 6px;
  background: white;
  transition: all 0.3s;
}

.stage-item.active {
  background: #e6f7ff;
  border: 1px solid #91d5ff;
}

.stage-item.completed {
  opacity: 0.7;
}

.stage-item.failed {
  background: #fff2f0;
  border: 1px solid #ffccc7;
}

.stage-icon {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.pending-icon {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #f0f0f0;
  color: #999;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.completed-icon {
  color: #52c41a;
  font-size: 18px;
}

.failed-icon {
  color: #ff4d4f;
  font-size: 18px;
}

.stage-info {
  flex: 1;
  min-width: 0;
}

.stage-name {
  font-size: 13px;
  font-weight: 500;
  color: #1a1a1a;
  margin-bottom: 4px;
}

.stage-desc {
  font-size: 12px;
  color: #666;
  margin-bottom: 6px;
}

.stage-details {
  color: #1890ff;
  margin-left: 4px;
}

.error-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: #fff2f0;
  border-radius: 6px;
  color: #ff4d4f;
  font-size: 13px;
}

.success-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: #f6ffed;
  border-radius: 6px;
  color: #52c41a;
  font-size: 13px;
}
</style>
