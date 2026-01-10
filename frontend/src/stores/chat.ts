import { defineStore } from 'pinia'
import { chatApi } from '@/api'
import type { Conversation, Message, ChatRequest, SourceDoc } from '@/types'
import { message as antMessage } from 'ant-design-vue'

// 处理阶段枚举
export type ProcessingStage = 'idle' | 'sending' | 'retrieving' | 'generating' | 'done' | 'error'

// 对话级别的流式状态
interface ConversationStreamState {
  processingStage: ProcessingStage
  processingStatus: string
  streamingContent: string
  streamingMessageId: number | null
  streamingSources: SourceDoc[]
  isSending: boolean
  abortController: AbortController | null
}

interface ChatState {
  conversations: Conversation[]
  currentConversation: Conversation | null
  isLoading: boolean
  // 每个对话的消息映射：conversationId -> Message[]
  messagesMap: Map<number, Message[]>
  // 每个对话的流式状态映射：conversationId -> ConversationStreamState
  streamStatesMap: Map<number, ConversationStreamState>
  // 新对话（未创建conversationId）的临时状态
  newConversationStreamState: ConversationStreamState | null
}

export const useChatStore = defineStore('chat', {
  state: (): ChatState => ({
    conversations: [],
    currentConversation: null,
    isLoading: false,
    messagesMap: new Map(),
    streamStatesMap: new Map(),
    newConversationStreamState: null,
  }),

  getters: {
    // 获取当前对话的消息列表
    messages(): Message[] {
      if (!this.currentConversation) {
        return this.newConversationStreamState ? [] : []
      }
      return this.messagesMap.get(this.currentConversation.id) || []
    },

    // 获取当前对话的流式状态
    currentStreamState(): ConversationStreamState | null {
      if (!this.currentConversation) {
        return this.newConversationStreamState
      }
      return this.streamStatesMap.get(this.currentConversation.id) || null
    },

    // 当前对话是否正在发送
    isSending(): boolean {
      const state = this.currentStreamState
      return state ? state.isSending : false
    },

    // 当前对话的处理阶段
    processingStage(): ProcessingStage {
      const state = this.currentStreamState
      return state ? state.processingStage : 'idle'
    },

    // 当前对话的处理状态文本
    processingStatus(): string {
      const state = this.currentStreamState
      return state ? state.processingStatus : ''
    },

    // 当前对话的流式内容
    streamingContent(): string {
      const state = this.currentStreamState
      return state ? state.streamingContent : ''
    },

    // 当前对话的流式来源
    streamingSources(): SourceDoc[] {
      const state = this.currentStreamState
      return state ? state.streamingSources : []
    },
  },

  actions: {
    // 创建新的流式状态
    _createStreamState(): ConversationStreamState {
      return {
        processingStage: 'idle',
        processingStatus: '',
        streamingContent: '',
        streamingMessageId: null,
        streamingSources: [],
        isSending: false,
        abortController: null,
      }
    },

    // 获取或创建对话的流式状态
    _getOrCreateStreamState(conversationId: number): ConversationStreamState {
      let state = this.streamStatesMap.get(conversationId)
      if (!state) {
        state = this._createStreamState()
        this.streamStatesMap.set(conversationId, state)
      }
      return state
    },

    // 获取或创建对话的消息列表
    _getOrCreateMessages(conversationId: number): Message[] {
      let messages = this.messagesMap.get(conversationId)
      if (!messages) {
        messages = []
        this.messagesMap.set(conversationId, messages)
      }
      return messages
    },

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
        // 初始化对话的消息列表和流式状态
        this.messagesMap.set(res.data.id, [])
        this.streamStatesMap.set(res.data.id, this._createStreamState())
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
        
        // 如果该对话的消息还未加载，从服务器获取
        if (!this.messagesMap.has(conversationId)) {
          await this.fetchMessages(conversationId)
        }
        
        // 确保有流式状态
        this._getOrCreateStreamState(conversationId)
        
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
        const messages = res.data
        
        // 为从数据库加载的消息分配roundId
        // 规则：USER消息开始新一轮，后续BOT消息属于同一轮，直到下一个USER消息
        let currentRoundId: number | null = null
        messages.forEach((msg, index) => {
          if (msg.senderType === 'USER') {
            // USER消息开始新一轮
            currentRoundId = msg.id
            msg.roundId = currentRoundId
          } else if (msg.senderType === 'BOT' && currentRoundId) {
            // BOT消息属于当前轮
            msg.roundId = currentRoundId
            
            // 检查是否是该轮最后一条BOT消息
            // 向后查找，如果下一条是USER或没有下一条了，则是最后一条
            const nextMsg = messages[index + 1]
            if (!nextMsg || nextMsg.senderType === 'USER') {
              msg.isLastInRound = true
            } else {
              msg.isLastInRound = false
            }
          }
        })
        
        this.messagesMap.set(conversationId, messages)
        return true
      } catch (error) {
        console.error('Fetch messages error:', error)
        return false
      }
    },

    // 发送消息（流式）
    async sendMessageStream(content: string, conversationId?: number) {
      // 确定使用哪个流式状态：已有对话 or 新对话
      let streamState: ConversationStreamState
      let messages: Message[]
      
      if (conversationId) {
        // 已有对话：获取流式状态和消息列表
        streamState = this._getOrCreateStreamState(conversationId)
        messages = this._getOrCreateMessages(conversationId)
        // 注意：不再取消该对话之前的请求，允许多个请求并发
        // 每个对话可以有多个并发的流式请求
      } else {
        // 新对话：创建临时流式状态
        // 注意：不再取消新对话之前的请求，允许多个新对话并发
        this.newConversationStreamState = this._createStreamState()
        streamState = this.newConversationStreamState
        messages = []
      }
      
      streamState.isSending = true
      streamState.processingStage = 'sending'
      streamState.processingStatus = '正在发送...'
      streamState.streamingContent = ''
      streamState.streamingMessageId = null
      streamState.streamingSources = []

      // 生成本轮对话的roundId
      const currentRoundId = Date.now()

      // 添加用户消息到界面
      const userMessage: Message = {
        id: Date.now(),
        conversationId: conversationId || 0,
        senderType: 'USER',
        content,
        createdAt: new Date().toISOString(),
        roundId: currentRoundId,
      }
      messages.push(userMessage)
      
      // 如果是已有对话，更新 messagesMap
      if (conversationId) {
        this.messagesMap.set(conversationId, messages)
      }

      const request: ChatRequest = {
        message: content,
        conversationId,
      }

      let newConversationId: number | undefined
      let newMessageId: number | undefined
      let conversationAdded = false
      const questionTitle = content.slice(0, 30) + (content.length > 30 ? '...' : '')

      // 发起流式请求
      streamState.abortController = chatApi.sendMessageStream(request, {
        onStatus: (stage, convId, msgId) => {
          // 检测新消息标记：保存当前内容为一条消息，开始新的流式内容
          if (stage === '__NEW_MESSAGE__') {
            if (streamState.streamingContent.trim()) {
              const finalMessages = (newConversationId || conversationId) 
                ? this._getOrCreateMessages(newConversationId || conversationId)
                : messages
              
              // 中间消息不保存sources，只保存内容
              const intermediateMessage: Message = {
                id: Date.now() + Math.random(),
                conversationId: newConversationId || conversationId || 0,
                senderType: 'BOT',
                content: streamState.streamingContent,
                metadata: {
                  isIntermediate: true, // 标记为中间消息
                  noAnimation: true, // 不需要动画（已在流式框中显示过）
                },
                createdAt: new Date().toISOString(),
                roundId: currentRoundId,
                isLastInRound: false,
              }
              finalMessages.push(intermediateMessage)
              
              // 清空流式内容，开始新回答（sources继续累加）
              streamState.streamingContent = ''
            }
            return
          }
          
          streamState.processingStatus = stage
          // 保存 conversationId 和 messageId
          if (convId) newConversationId = convId
          if (msgId) newMessageId = msgId
          
          // 问题1和2：收到第一个状态时立即添加新会话到列表
          if (!conversationId && convId && !conversationAdded) {
            conversationAdded = true
            const newConv: Conversation = {
              id: convId,
              userId: 0,
              title: questionTitle,
              messageCount: 1,
              status: 'ACTIVE',
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString(),
            }
            this.currentConversation = newConv
            this.conversations.unshift(newConv)
            
            // 将临时消息和状态迁移到新对话
            this.messagesMap.set(convId, messages)
            this.streamStatesMap.set(convId, streamState)
            this.newConversationStreamState = null
            
            // 更新用户消息的 conversationId
            userMessage.conversationId = convId
          }
          
          // 根据状态文本判断处理阶段
          if (stage.includes('检索')) {
            streamState.processingStage = 'retrieving'
          } else if (stage.includes('生成')) {
            streamState.processingStage = 'generating'
          }
        },
        
        onSources: (sources, convId, msgId) => {
          if (convId) newConversationId = convId
          if (msgId) newMessageId = msgId
          // 后端发送的是累加后的全部sources，直接替换
          streamState.streamingSources = sources
        },
        
        onContent: (chunk, convId, msgId) => {
          if (convId) newConversationId = convId
          if (msgId) newMessageId = msgId
          streamState.processingStage = 'generating'
          streamState.streamingContent += chunk
        },
        
        onDone: (usage, responseTimeMs, convId, msgId) => {
          if (convId) newConversationId = convId
          if (msgId) newMessageId = msgId
          
          streamState.processingStage = 'done'
          streamState.processingStatus = ''
          streamState.isSending = false
          
          // 获取最终的消息列表（可能已经迁移）
          const finalMessages = convId ? this._getOrCreateMessages(convId) : messages
          
          // 只有当有内容时才添加最终消息
          if (streamState.streamingContent.trim()) {
            const botMessage: Message = {
              id: newMessageId || Date.now() + 1,
              conversationId: newConversationId || conversationId || 0,
              senderType: 'BOT',
              content: streamState.streamingContent,
              metadata: {
                sources: [...streamState.streamingSources],
                usage: usage,
                responseTimeMs: responseTimeMs,
                noAnimation: true, // 标记为不需要动画（流式转正式）
              },
              createdAt: new Date().toISOString(),
              roundId: currentRoundId,
              isLastInRound: true,
            }
            finalMessages.push(botMessage)
          }
          
          // 更新会话消息数
          const targetConvId = newConversationId || conversationId
          if (targetConvId) {
            const conv = this.conversations.find((c) => c.id === targetConvId)
            if (conv) {
              conv.messageCount = finalMessages.length
            }
            if (this.currentConversation?.id === targetConvId) {
              this.currentConversation.messageCount = finalMessages.length
            }
          }
          
          // 清理流式状态
          streamState.streamingContent = ''
          streamState.streamingMessageId = null
          streamState.streamingSources = []
          streamState.abortController = null
          streamState.processingStage = 'idle'
        },
        
        onError: (error) => {
          console.error('Stream error:', error)
          
          // 如果是 AbortError，说明是被主动取消的，不显示错误
          if (error.name === 'AbortError') {
            streamState.processingStage = 'idle'
            streamState.processingStatus = ''
            streamState.isSending = false
            streamState.abortController = null
            return
          }
          
          streamState.processingStage = 'error'
          streamState.processingStatus = `错误: ${error}`
          streamState.isSending = false
          
          // 移除失败的用户消息
          const finalMessages = newConversationId ? this._getOrCreateMessages(newConversationId) : messages
          finalMessages.pop()
          
          // 根据错误类型显示不同的提示
          const errorMessage = error.toString()
          if (errorMessage.includes('系统繁忙') || errorMessage.includes('504')) {
            antMessage.warning('系统繁忙，请稍后再试')
          } else if (errorMessage.includes('请求过于频繁') || errorMessage.includes('429')) {
            antMessage.warning('请求过于频繁，请稍后再试')
          } else if (errorMessage.includes('SSE连接数已达上限')) {
            antMessage.warning('连接数已达上限，请稍后再试')
          } else {
            antMessage.error('发送消息失败: ' + error)
          }
          
          // 清理状态
          setTimeout(() => {
            streamState.processingStage = 'idle'
            streamState.processingStatus = ''
            streamState.abortController = null
          }, 3000)
        },
      })
      
      return conversationId
    },

    // 取消流式请求
    cancelStream(conversationId?: number) {
      if (conversationId) {
        const state = this.streamStatesMap.get(conversationId)
        if (state?.abortController) {
          state.abortController.abort()
          state.abortController = null
        }
      } else if (this.newConversationStreamState?.abortController) {
        this.newConversationStreamState.abortController.abort()
        this.newConversationStreamState.abortController = null
      }
    },

    // 发送消息（非流式，保留作为备用）
    async sendMessage(content: string, conversationId?: number) {
      try {
        const streamState = conversationId 
          ? this._getOrCreateStreamState(conversationId)
          : (this.newConversationStreamState || this._createStreamState())
        const messages = conversationId 
          ? this._getOrCreateMessages(conversationId)
          : []
        
        streamState.isSending = true

        // 添加用户消息到界面
        const userMessage: Message = {
          id: Date.now(),
          conversationId: conversationId || 0,
          senderType: 'USER',
          content,
          createdAt: new Date().toISOString(),
        }
        messages.push(userMessage)

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
          const newConv: Conversation = {
            id: chatResponse.conversationId,
            userId: 0,
            title: content.slice(0, 30),
            messageCount: 2,
            status: 'ACTIVE',
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          }
          this.currentConversation = newConv
          this.conversations.unshift(newConv)
          
          // 迁移消息到新对话
          this.messagesMap.set(chatResponse.conversationId, messages)
          this.streamStatesMap.set(chatResponse.conversationId, streamState)
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
        messages.push(botMessage)

        // 更新会话消息数
        const finalConvId = chatResponse.conversationId || conversationId
        if (finalConvId) {
          const conv = this.conversations.find((c) => c.id === finalConvId)
          if (conv) {
            conv.messageCount = messages.length
          }
          if (this.currentConversation?.id === finalConvId) {
            this.currentConversation.messageCount = messages.length
          }
        }

        return chatResponse
      } catch (error) {
        console.error('Send message error:', error)
        // 移除失败的用户消息
        const messages = conversationId 
          ? this._getOrCreateMessages(conversationId)
          : []
        messages.pop()
        return null
      } finally {
        const streamState = conversationId 
          ? this._getOrCreateStreamState(conversationId)
          : this.newConversationStreamState
        if (streamState) {
          streamState.isSending = false
        }
      }
    },

    // 删除会话
    async deleteConversation(conversationId: number) {
      try {
        await chatApi.deleteConversation(conversationId)
        this.conversations = this.conversations.filter((c) => c.id !== conversationId)
        
        // 清理该对话的数据
        this.messagesMap.delete(conversationId)
        const state = this.streamStatesMap.get(conversationId)
        if (state?.abortController) {
          state.abortController.abort()
        }
        this.streamStatesMap.delete(conversationId)
        
        if (this.currentConversation?.id === conversationId) {
          this.currentConversation = null
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
      // 注意：这个方法只清空当前对话状态，不取消任何 SSE 连接
      // 每个对话的 SSE 连接应该独立运行，互不干扰
      this.currentConversation = null
      this.newConversationStreamState = null
    },

    // 清理所有对话数据（用于退出登录时）
    clearAll() {
      this.conversations = []
      this.currentConversation = null
      this.messagesMap.clear()
      this.streamStatesMap.clear()
      this.newConversationStreamState = null
    },
  },
})
