import { request } from '@/utils/request'
import type {
  LoginRequest,
  LoginResponse,
  User,
  ChatRequest,
  ChatResponse,
  Conversation,
  Message,
  KnowledgeDoc,
  KnowledgeCategory,
  KnowledgeDocRequest,
  PageResult,
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
  sendMessageStream(message: string, conversationId?: number) {
    const params = new URLSearchParams()
    params.append('message', message)
    if (conversationId) {
      params.append('conversationId', conversationId.toString())
    }
    
    const url = `/api/v1/chat/message/stream?${params.toString()}`
    
    return new EventSource(url, {
      withCredentials: false,
    })
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
}
