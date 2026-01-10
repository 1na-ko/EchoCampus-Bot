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
  roundId?: number // 同一轮对话的标识
  isLastInRound?: boolean // 是否是该轮最后一条消息
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

// 文档处理进度类型
export type DocumentProcessStage = 
  | 'UPLOADING'   // 文件上传中
  | 'PARSING'     // 文档解析中
  | 'CHUNKING'    // 文本切块中
  | 'EMBEDDING'   // 向量化处理中
  | 'STORING'     // 数据存储中
  | 'COMPLETED'   // 处理完成
  | 'FAILED'      // 处理失败
  | 'PENDING'     // 等待处理
  | 'PROCESSING'  // 处理中

export interface DocumentProgress {
  docId: number
  stage: DocumentProcessStage
  stageName: string
  progress: number       // 当前阶段进度 (0-100)
  totalProgress: number  // 总体进度 (0-100)
  message: string
  details?: string
  completed: boolean
  failed: boolean
  errorMessage?: string
}
