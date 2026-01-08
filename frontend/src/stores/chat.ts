import { defineStore } from 'pinia'
import { chatApi } from '@/api'
import type { Conversation, Message, ChatRequest, SourceDoc } from '@/types'
import { message as antMessage } from 'ant-design-vue'

// 处理阶段枚举
export type ProcessingStage = 'idle' | 'sending' | 'retrieving' | 'generating' | 'done' | 'error'

interface ChatState {
  conversations: Conversation[]
  currentConversation: Conversation | null
  messages: Message[]
  isLoading: boolean
  isSending: boolean
  // 流式输出相关状态
  processingStage: ProcessingStage
  processingStatus: string
  streamingContent: string
  streamingMessageId: number | null
  streamingSources: SourceDoc[]
  // SSE控制器
  abortController: AbortController | null
}

export const useChatStore = defineStore('chat', {
  state: (): ChatState => ({
    conversations: [],
    currentConversation: null,
    messages: [],
    isLoading: false,
    isSending: false,
    // 流式输出相关状态
    processingStage: 'idle',
    processingStatus: '',
    streamingContent: '',
    streamingMessageId: null,
    streamingSources: [],
    // SSE控制器
    abortController: null,
  }),

  actions: {
    // 获取会话列表
    async fetchConversations(page = 1, size = 20) {
      try {
        this.isLoading = true
        const res = await chatApi.getConversations(page, size)
        this.conversations = res.data
        return true
      } catch (error) {
        console.error('Fetch conversations error:', error)
        return false
      } finally {
        this.isLoading = false
      }
    },

    // 创建新会话
    async createConversation(title?: string) {
      try {
        const res = await chatApi.createConversation(title)
        this.currentConversation = res.data
        this.messages = []
        this.conversations.unshift(res.data)
        return res.data
      } catch (error) {
        console.error('Create conversation error:', error)
        return null
      }
    },

    // 选择会话
    async selectConversation(conversationId: number) {
      try {
        this.isLoading = true
        const conversation = this.conversations.find((c) => c.id === conversationId)
        if (conversation) {
          this.currentConversation = conversation
        }
        await this.fetchMessages(conversationId)
        return true
      } catch (error) {
        console.error('Select conversation error:', error)
        return false
      } finally {
        this.isLoading = false
      }
    },

    // 获取会话消息
    async fetchMessages(conversationId: number) {
      try {
        const res = await chatApi.getMessages(conversationId)
        this.messages = res.data
        return true
      } catch (error) {
        console.error('Fetch messages error:', error)
        return false
      }
    },

    // 发送消息（流式）
    async sendMessageStream(content: string, conversationId?: number) {
      // 取消之前的请求
      this.cancelStream()
      
      this.isSending = true
      this.processingStage = 'sending'
      this.processingStatus = '正在发送...'
      this.streamingContent = ''
      this.streamingMessageId = null
      this.streamingSources = []

      // 添加用户消息到界面
      const userMessage: Message = {
        id: Date.now(),
        conversationId: conversationId || 0,
        senderType: 'USER',
        content,
        createdAt: new Date().toISOString(),
      }
      this.messages.push(userMessage)

      const request: ChatRequest = {
        message: content,
        conversationId,
      }

      let newConversationId: number | undefined
      let newMessageId: number | undefined
      let conversationAdded = false
      const questionTitle = content.slice(0, 30) + (content.length > 30 ? '...' : '')

      // 发起流式请求
      this.abortController = chatApi.sendMessageStream(request, {
        onStatus: (stage, convId, msgId) => {
          this.processingStatus = stage
          // 保存 conversationId 和 messageId
          if (convId) newConversationId = convId
          if (msgId) newMessageId = msgId
          
          // 问题1和2：收到第一个状态时立即添加新会话到列表
          if (!conversationId && convId && !conversationAdded) {
            conversationAdded = true
            this.currentConversation = {
              id: convId,
              userId: 0,
              title: questionTitle,
              messageCount: 1,
              status: 'ACTIVE',
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString(),
            }
            this.conversations.unshift(this.currentConversation)
            // 标题已由后端在创建会话时设置，无需额外调用 API
          }
          
          // 根据状态文本判断处理阶段
          if (stage.includes('检索')) {
            this.processingStage = 'retrieving'
          } else if (stage.includes('生成')) {
            this.processingStage = 'generating'
          }
        },
        
        onSources: (sources, convId, msgId) => {
          if (convId) newConversationId = convId
          if (msgId) newMessageId = msgId
          this.streamingSources = sources
        },
        
        onContent: (chunk, convId, msgId) => {
          if (convId) newConversationId = convId
          if (msgId) newMessageId = msgId
          this.processingStage = 'generating'
          this.streamingContent += chunk
        },
        
        onDone: (usage, responseTimeMs, convId, msgId) => {
          if (convId) newConversationId = convId
          if (msgId) newMessageId = msgId
          
          this.processingStage = 'done'
          this.processingStatus = ''
          this.isSending = false
          
          // 创建最终的AI消息
          const botMessage: Message = {
            id: newMessageId || Date.now() + 1,
            conversationId: newConversationId || conversationId || 0,
            senderType: 'BOT',
            content: this.streamingContent,
            metadata: {
              sources: this.streamingSources,
              usage: usage,
              responseTimeMs: responseTimeMs,
            },
            createdAt: new Date().toISOString(),
          }
          this.messages.push(botMessage)
          
          // 更新会话消息数（会话已在onStatus中添加）
          if (this.currentConversation) {
            this.currentConversation.messageCount = 2
          }
          
          // 清理流式状态
          this.streamingContent = ''
          this.streamingMessageId = null
          this.streamingSources = []
          this.abortController = null
          this.processingStage = 'idle'
        },
        
        onError: (error) => {
          console.error('Stream error:', error)
          this.processingStage = 'error'
          this.processingStatus = `错误: ${error}`
          this.isSending = false
          
          // 移除失败的用户消息
          this.messages.pop()
          
          antMessage.error('发送消息失败: ' + error)
          
          // 清理状态
          setTimeout(() => {
            this.processingStage = 'idle'
            this.processingStatus = ''
          }, 3000)
        },
      })
      
      return conversationId
    },

    // 取消流式请求
    cancelStream() {
      if (this.abortController) {
        this.abortController.abort()
        this.abortController = null
      }
    },

    // 发送消息（非流式，保留作为备用）
    async sendMessage(content: string, conversationId?: number) {
      try {
        this.isSending = true

        // 添加用户消息到界面
        const userMessage: Message = {
          id: Date.now(),
          conversationId: conversationId || 0,
          senderType: 'USER',
          content,
          createdAt: new Date().toISOString(),
        }
        this.messages.push(userMessage)

        const request: ChatRequest = {
          message: content,
          conversationId,
        }

        const res = await chatApi.sendMessage(request)
        const chatResponse = res.data

        console.log('ChatResponse received:', chatResponse)
        console.log('Sources:', chatResponse.sources)

        // 更新会话ID
        if (!conversationId && chatResponse.conversationId) {
          this.currentConversation = {
            id: chatResponse.conversationId,
            userId: 0,
            title: content.slice(0, 30),
            messageCount: 2,
            status: 'ACTIVE',
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          }
          this.conversations.unshift(this.currentConversation)
        }

        // 添加AI回复
        const botMessage: Message = {
          id: chatResponse.messageId,
          conversationId: chatResponse.conversationId,
          senderType: 'BOT',
          content: chatResponse.answer,
          metadata: {
            sources: chatResponse.sources,
            usage: chatResponse.usage,
            responseTimeMs: chatResponse.responseTimeMs,
          },
          createdAt: chatResponse.createdAt,
        }
        this.messages.push(botMessage)

        // 更新会话消息数
        if (this.currentConversation) {
          this.currentConversation.messageCount += 2
        }

        return chatResponse
      } catch (error) {
        console.error('Send message error:', error)
        // 移除失败的用户消息
        this.messages.pop()
        return null
      } finally {
        this.isSending = false
      }
    },

    // 删除会话
    async deleteConversation(conversationId: number) {
      try {
        await chatApi.deleteConversation(conversationId)
        this.conversations = this.conversations.filter((c) => c.id !== conversationId)
        if (this.currentConversation?.id === conversationId) {
          this.currentConversation = null
          this.messages = []
        }
        antMessage.success('会话已删除')
        return true
      } catch (error) {
        console.error('Delete conversation error:', error)
        return false
      }
    },

    // 更新会话标题
    async updateConversationTitle(conversationId: number, title: string) {
      try {
        await chatApi.updateConversationTitle(conversationId, title)
        const conversation = this.conversations.find((c) => c.id === conversationId)
        if (conversation) {
          conversation.title = title
        }
        if (this.currentConversation?.id === conversationId) {
          this.currentConversation.title = title
        }
        antMessage.success('标题已更新')
        return true
      } catch (error) {
        console.error('Update conversation title error:', error)
        return false
      }
    },

    // 清空当前会话
    clearCurrentConversation() {
      this.currentConversation = null
      this.messages = []
      this.cancelStream()
      this.processingStage = 'idle'
      this.processingStatus = ''
      this.streamingContent = ''
    },
  },
})
