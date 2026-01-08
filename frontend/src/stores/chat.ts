import { defineStore } from 'pinia'
import { chatApi } from '@/api'
import type { Conversation, Message, ChatRequest } from '@/types'
import { message as antMessage } from 'ant-design-vue'

interface ChatState {
  conversations: Conversation[]
  currentConversation: Conversation | null
  messages: Message[]
  isLoading: boolean
  isSending: boolean
}

export const useChatStore = defineStore('chat', {
  state: (): ChatState => ({
    conversations: [],
    currentConversation: null,
    messages: [],
    isLoading: false,
    isSending: false,
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

    // 发送消息
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
    },
  },
})
