import { request, createSSERequest } from '@/utils/request'
import type {
  LoginRequest,
  LoginResponse,
  User,
  ChatRequest,
  ChatResponse,
  StreamChatResponse,
  Conversation,
  Message,
  KnowledgeDoc,
  KnowledgeCategory,
  KnowledgeDocRequest,
  PageResult,
  DocumentProgress,
} from '@/types'

// ==================== 用户认证 API ====================
export const authApi = {
  // 用户登录
  login(data: LoginRequest) {
    return request.post<LoginResponse>('/v1/auth/login', data)
  },

  // 用户注册
  register(data: Partial<User>) {
    return request.post<User>('/v1/auth/register', data)
  },

  // 获取当前用户信息
  getCurrentUser() {
    return request.get<User>('/v1/user/profile')
  },

  // 更新用户信息
  updateProfile(data: Partial<User>) {
    return request.put<void>('/v1/user/profile', data)
  },

  // 修改密码
  changePassword(oldPassword: string, newPassword: string) {
    return request.put<void>('/v1/user/password', null, {
      params: { oldPassword, newPassword },
    })
  },
}

// ==================== 聊天 API ====================
export const chatApi = {
  // 发送消息
  sendMessage(data: ChatRequest) {
    return request.post<ChatResponse>('/v1/chat/message', data)
  },

  // 发送消息（流式）
  sendMessageStream(
    data: ChatRequest,
    callbacks: {
      onStatus?: (stage: string, conversationId?: number, messageId?: number) => void
      onSources?: (sources: any[], conversationId?: number, messageId?: number) => void
      onContent?: (chunk: string, conversationId?: number, messageId?: number) => void
      onDone?: (usage: any, responseTimeMs: number, conversationId?: number, messageId?: number) => void
      onError?: (error: string) => void
    }
  ): AbortController {
    return createSSERequest(
      '/v1/chat/message/stream',
      data,
      (event) => {
        try {
          const response: StreamChatResponse = JSON.parse(event.data)
          // 将 type 转为小写以匹配 TypeScript 类型定义
          const eventType = response.type?.toLowerCase() as StreamChatResponse['type']
          
          switch (eventType) {
            case 'status':
              callbacks.onStatus?.(response.stage || '', response.conversationId, response.messageId)
              break
            case 'sources':
              callbacks.onSources?.(response.sources || [], response.conversationId, response.messageId)
              break
            case 'content':
              callbacks.onContent?.(response.content || '', response.conversationId, response.messageId)
              break
            case 'done':
              callbacks.onDone?.(response.usage, response.responseTimeMs || 0, response.conversationId, response.messageId)
              break
            case 'error':
              callbacks.onError?.(response.error || '未知错误')
              break
          }
        } catch (e) {
          console.error('解析SSE数据失败:', e, event.data)
        }
      },
      (error) => {
        callbacks.onError?.(error.message)
      }
    )
  },

  // 获取会话列表
  getConversations(page = 1, size = 10) {
    return request.get<Conversation[]>('/v1/chat/conversations', {
      params: { page, size },
    })
  },

  // 获取会话消息
  getMessages(conversationId: number) {
    return request.get<Message[]>(`/v1/chat/conversations/${conversationId}/messages`)
  },

  // 创建新会话
  createConversation(title = '新对话') {
    return request.post<Conversation>('/v1/chat/conversations', null, {
      params: { title },
    })
  },

  // 删除会话
  deleteConversation(conversationId: number) {
    return request.delete<void>(`/v1/chat/conversations/${conversationId}`)
  },

  // 更新会话标题
  updateConversationTitle(conversationId: number, title: string) {
    return request.put<void>(`/v1/chat/conversations/${conversationId}`, null, {
      params: { title },
    })
  },
}

// ==================== 知识库 API ====================
export const knowledgeApi = {
  // 上传文档
  uploadDocument(file: File, data: KnowledgeDocRequest) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('title', data.title)
    if (data.description) formData.append('description', data.description)
    if (data.category) formData.append('category', data.category)
    if (data.tags) formData.append('tags', data.tags)

    return request.upload<KnowledgeDoc>('/v1/knowledge/docs', formData)
  },

  // 获取文档列表
  getDocuments(params: {
    category?: string
    status?: string
    keyword?: string
    page?: number
    size?: number
  }) {
    return request.get<PageResult<KnowledgeDoc>>('/v1/knowledge/docs', { params })
  },

  // 获取文档详情
  getDocument(docId: number) {
    return request.get<KnowledgeDoc>(`/v1/knowledge/docs/${docId}`)
  },

  // 更新文档
  updateDocument(docId: number, data: KnowledgeDocRequest) {
    return request.put<void>(`/v1/knowledge/docs/${docId}`, data)
  },

  // 删除文档
  deleteDocument(docId: number) {
    return request.delete<void>(`/v1/knowledge/docs/${docId}`)
  },

  // 重新索引文档
  reindexDocument(docId: number) {
    return request.post<void>(`/v1/knowledge/docs/${docId}/reindex`)
  },

  // 获取分类列表
  getCategories() {
    return request.get<KnowledgeCategory[]>('/v1/knowledge/categories')
  },

  // 获取当前进度
  getCurrentProgress(docId: number) {
    return request.get<DocumentProgress>(`/v1/knowledge/docs/${docId}/progress/current`)
  },

  // 订阅文档处理进度 (SSE)
  subscribeProgress(
    docId: number,
    callbacks: {
      onProgress?: (progress: DocumentProgress) => void
      onError?: (error: string) => void
      onComplete?: () => void
    }
  ): EventSource {
    const url = new URL(`/api/v1/knowledge/docs/${docId}/progress`, window.location.origin)
    
    const eventSource = new EventSource(url.toString())
    
    eventSource.addEventListener('progress', (event: MessageEvent) => {
      try {
        const progress: DocumentProgress = JSON.parse(event.data)
        callbacks.onProgress?.(progress)
        
        // 如果完成或失败，关闭连接
        if (progress.completed || progress.failed) {
          eventSource.close()
          callbacks.onComplete?.()
        }
      } catch (e) {
        console.error('解析进度数据失败:', e, event.data)
      }
    })
    
    eventSource.onerror = (error) => {
      console.error('SSE连接错误:', error)
      
      // 根据EventSource的状态判断错误类型
      if (eventSource.readyState === EventSource.CLOSED) {
        callbacks.onError?.('连接已关闭')
      } else if (eventSource.readyState === EventSource.CONNECTING) {
        callbacks.onError?.('正在重新连接...')
      } else {
        callbacks.onError?.('连接错误')
      }
      
      eventSource.close()
    }
    
    return eventSource
  },
}
