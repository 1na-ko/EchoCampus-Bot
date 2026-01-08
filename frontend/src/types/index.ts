// API响应基础类型
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  timestamp: number
  requestId?: string
}

// 分页响应
export interface PageResult<T> {
  total: number
  page: number
  size: number
  list: T[]
}

// 用户相关类型
export interface User {
  id: number
  username: string
  nickname: string
  email: string
  avatar?: string
  role: string
  status: string
  lastLoginAt?: string
  createdAt: string
  updatedAt: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  userId: number
  username: string
  nickname: string
  email: string
  role: string
  token: string
  expireAt: number
}

// 对话相关类型
export interface Conversation {
  id: number
  userId: number
  title: string
  messageCount: number
  status: string
  createdAt: string
  updatedAt: string
}

export interface Message {
  id: number
  conversationId: number
  parentMessageId?: number
  senderType: 'USER' | 'BOT' | 'SYSTEM'
  content: string
  tokenCount?: number
  metadata?: Record<string, any>
  createdAt: string
}

export interface ChatRequest {
  conversationId?: number
  message: string
  enableContext?: boolean
  contextRounds?: number
}

export interface SourceDoc {
  docId: number
  title: string
  content: string
  similarity: number
  category?: string
}

export interface TokenUsage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

export interface ChatResponse {
  messageId: number
  conversationId: number
  answer: string
  sources?: SourceDoc[]
  usage?: TokenUsage
  responseTimeMs?: number
  createdAt: string
}

// 流式聊天响应类型
export type StreamEventType = 'status' | 'sources' | 'content' | 'done' | 'error'

export interface StreamChatResponse {
  type: StreamEventType
  conversationId?: number
  messageId?: number
  content?: string
  stage?: string
  sources?: SourceDoc[]
  usage?: TokenUsage
  responseTimeMs?: number
  error?: string
}

// 知识库相关类型
export interface KnowledgeDoc {
  id: number
  title: string
  description?: string
  fileName: string
  filePath: string
  fileSize: number
  fileType: string
  category?: string
  tags?: string
  status: string
  vectorCount?: number
  processStatus?: string
  processMessage?: string
  lastIndexedAt?: string
  createdBy: number
  createdAt: string
  updatedAt: string
}

export interface KnowledgeCategory {
  id: number
  name: string
  description?: string
  parentId?: number
  sortOrder: number
  docCount: number
  createdAt: string
  updatedAt: string
}

export interface KnowledgeDocRequest {
  title: string
  description?: string
  category?: string
  tags?: string
}

export interface UploadDocRequest extends KnowledgeDocRequest {
  file: File
}
