import { describe, it, expect, beforeEach, vi, afterEach, Mock } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useChatStore } from '@/stores/chat'
import { chatApi } from '@/api'

// Mock API模块
vi.mock('@/api', () => ({
  chatApi: {
    getConversations: vi.fn(),
    createConversation: vi.fn(),
    getMessages: vi.fn(),
    sendMessage: vi.fn(),
    sendMessageStream: vi.fn(),
    deleteConversation: vi.fn(),
    updateConversationTitle: vi.fn(),
  },
}))

// Mock ant-design-vue message
vi.mock('ant-design-vue', () => ({
  message: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    warning: vi.fn(),
  },
}))

/**
 * Chat Store 单元测试
 * P3 优先级 - 前端状态管理
 */
describe('Chat Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('初始状态', () => {
    it('应该有正确的初始状态', () => {
      const store = useChatStore()

      expect(store.conversations).toEqual([])
      expect(store.currentConversation).toBeNull()
      expect(store.isLoading).toBe(false)
      expect(store.messagesMap.size).toBe(0)
      expect(store.streamStatesMap.size).toBe(0)
    })
  })

  describe('Getters', () => {
    it('messages getter 应该返回当前对话的消息', () => {
      const store = useChatStore()
      
      // 没有当前对话时应该返回空数组
      expect(store.messages).toEqual([])

      // 设置当前对话
      store.currentConversation = { id: 1, userId: 1, title: '测试', messageCount: 0, status: 'ACTIVE' } as any
      store.messagesMap.set(1, [
        { id: 1, conversationId: 1, content: '测试消息', senderType: 'USER' } as any,
      ])

      expect(store.messages).toHaveLength(1)
      expect(store.messages[0].content).toBe('测试消息')
    })

    it('isSending getter 应该返回当前发送状态', () => {
      const store = useChatStore()

      expect(store.isSending).toBe(false)

      store.currentConversation = { id: 1, userId: 1, title: '测试', messageCount: 0, status: 'ACTIVE' } as any
      store.streamStatesMap.set(1, {
        processingStage: 'sending',
        processingStatus: '正在发送...',
        streamingContent: '',
        streamingMessageId: null,
        streamingSources: [],
        isSending: true,
        abortController: null,
        doneReceived: false,
      })

      expect(store.isSending).toBe(true)
    })

    it('processingStage getter 应该返回正确的处理阶段', () => {
      const store = useChatStore()

      expect(store.processingStage).toBe('idle')

      store.currentConversation = { id: 1, userId: 1, title: '测试', messageCount: 0, status: 'ACTIVE' } as any
      store.streamStatesMap.set(1, {
        processingStage: 'generating',
        processingStatus: '正在生成...',
        streamingContent: '',
        streamingMessageId: null,
        streamingSources: [],
        isSending: true,
        abortController: null,
        doneReceived: false,
      })

      expect(store.processingStage).toBe('generating')
    })
  })

  describe('fetchConversations action', () => {
    it('获取会话列表成功应该更新状态', async () => {
      const store = useChatStore()
      const mockConversations = [
        { id: 1, title: '对话1', userId: 1, messageCount: 5, status: 'ACTIVE' },
        { id: 2, title: '对话2', userId: 1, messageCount: 3, status: 'ACTIVE' },
      ]

      ;(chatApi.getConversations as Mock).mockResolvedValueOnce({
        data: mockConversations,
      })

      const result = await store.fetchConversations(1, 20)

      expect(result).toBe(true)
      expect(store.conversations).toHaveLength(2)
      expect(store.conversations[0].title).toBe('对话1')
      expect(store.isLoading).toBe(false)
    })

    it('获取会话列表失败应该返回false', async () => {
      const store = useChatStore()

      ;(chatApi.getConversations as Mock).mockRejectedValueOnce(new Error('网络错误'))

      const result = await store.fetchConversations()

      expect(result).toBe(false)
      expect(store.conversations).toEqual([])
      expect(store.isLoading).toBe(false)
    })
  })

  describe('createConversation action', () => {
    it('创建会话成功应该添加到列表并设为当前会话', async () => {
      const store = useChatStore()
      const mockConversation = {
        id: 1,
        title: '新对话',
        userId: 1,
        messageCount: 0,
        status: 'ACTIVE',
      }

      ;(chatApi.createConversation as Mock).mockResolvedValueOnce({
        data: mockConversation,
      })

      const result = await store.createConversation('新对话')

      expect(result).not.toBeNull()
      expect(result?.id).toBe(1)
      expect(store.currentConversation).toEqual(mockConversation)
      expect(store.conversations).toContainEqual(mockConversation)
      expect(store.messagesMap.has(1)).toBe(true)
      expect(store.streamStatesMap.has(1)).toBe(true)
    })

    it('创建会话失败应该返回null', async () => {
      const store = useChatStore()

      ;(chatApi.createConversation as Mock).mockRejectedValueOnce(new Error('创建失败'))

      const result = await store.createConversation()

      expect(result).toBeNull()
    })
  })

  describe('selectConversation action', () => {
    it('选择已存在的会话应该设为当前会话', async () => {
      const store = useChatStore()
      store.conversations = [
        { id: 1, title: '对话1', userId: 1, messageCount: 0, status: 'ACTIVE' } as any,
        { id: 2, title: '对话2', userId: 1, messageCount: 0, status: 'ACTIVE' } as any,
      ]

      ;(chatApi.getMessages as Mock).mockResolvedValueOnce({ data: [] })

      const result = await store.selectConversation(1)

      expect(result).toBe(true)
      expect(store.currentConversation?.id).toBe(1)
      expect(store.currentConversation?.title).toBe('对话1')
    })

    it('选择会话时应该获取消息（如果未缓存）', async () => {
      const store = useChatStore()
      store.conversations = [
        { id: 1, title: '对话1', userId: 1, messageCount: 1, status: 'ACTIVE' } as any,
      ]

      const mockMessages = [
        { id: 1, conversationId: 1, content: '消息1', senderType: 'USER' },
      ]
      ;(chatApi.getMessages as Mock).mockResolvedValueOnce({ data: mockMessages })

      await store.selectConversation(1)

      expect(chatApi.getMessages).toHaveBeenCalledWith(1)
      expect(store.messagesMap.get(1)).toHaveLength(1)
    })

    it('选择会话时不应该重复获取已缓存的消息', async () => {
      const store = useChatStore()
      store.conversations = [
        { id: 1, title: '对话1', userId: 1, messageCount: 1, status: 'ACTIVE' } as any,
      ]
      store.messagesMap.set(1, [
        { id: 1, conversationId: 1, content: '已缓存消息', senderType: 'USER' } as any,
      ])

      await store.selectConversation(1)

      expect(chatApi.getMessages).not.toHaveBeenCalled()
    })
  })

  describe('fetchMessages action', () => {
    it('获取消息成功应该更新消息映射', async () => {
      const store = useChatStore()
      const mockMessages = [
        { id: 1, conversationId: 1, content: 'USER消息', senderType: 'USER' },
        { id: 2, conversationId: 1, content: 'BOT回复', senderType: 'BOT' },
      ]

      ;(chatApi.getMessages as Mock).mockResolvedValueOnce({ data: mockMessages })

      const result = await store.fetchMessages(1)

      expect(result).toBe(true)
      expect(store.messagesMap.get(1)).toHaveLength(2)
    })

    it('获取消息应该正确分配roundId', async () => {
      const store = useChatStore()
      const mockMessages = [
        { id: 1, conversationId: 1, content: '问题1', senderType: 'USER' },
        { id: 2, conversationId: 1, content: '回复1', senderType: 'BOT' },
        { id: 3, conversationId: 1, content: '问题2', senderType: 'USER' },
        { id: 4, conversationId: 1, content: '回复2', senderType: 'BOT' },
      ]

      ;(chatApi.getMessages as Mock).mockResolvedValueOnce({ data: mockMessages })

      await store.fetchMessages(1)

      const messages = store.messagesMap.get(1)!
      // 第一个USER消息的roundId应该是它自己的id
      expect(messages[0].roundId).toBe(1)
      // BOT回复应该继承同一轮的roundId
      expect(messages[1].roundId).toBe(1)
      // 第二个USER消息开始新一轮
      expect(messages[2].roundId).toBe(3)
      expect(messages[3].roundId).toBe(3)
    })

    it('获取消息失败应该返回false', async () => {
      const store = useChatStore()

      ;(chatApi.getMessages as Mock).mockRejectedValueOnce(new Error('获取失败'))

      const result = await store.fetchMessages(1)

      expect(result).toBe(false)
    })
  })

  describe('内部辅助方法', () => {
    it('_createStreamState 应该创建正确的初始流式状态', () => {
      const store = useChatStore()

      const state = store._createStreamState()

      expect(state.processingStage).toBe('idle')
      expect(state.processingStatus).toBe('')
      expect(state.streamingContent).toBe('')
      expect(state.streamingMessageId).toBeNull()
      expect(state.streamingSources).toEqual([])
      expect(state.isSending).toBe(false)
      expect(state.abortController).toBeNull()
      expect(state.doneReceived).toBe(false)
    })

    it('_getOrCreateStreamState 应该返回已存在的状态', () => {
      const store = useChatStore()
      const existingState = {
        processingStage: 'generating' as const,
        processingStatus: '正在生成...',
        streamingContent: '内容',
        streamingMessageId: 1,
        streamingSources: [],
        isSending: true,
        abortController: null,
        doneReceived: false,
      }
      store.streamStatesMap.set(1, existingState)

      const state = store._getOrCreateStreamState(1)

      expect(state).toBe(existingState)
      expect(state.processingStage).toBe('generating')
    })

    it('_getOrCreateStreamState 应该创建新状态如果不存在', () => {
      const store = useChatStore()

      const state = store._getOrCreateStreamState(999)

      expect(state).toBeDefined()
      expect(state.processingStage).toBe('idle')
      expect(store.streamStatesMap.has(999)).toBe(true)
    })

    it('_getOrCreateMessages 应该返回已存在的消息列表', () => {
      const store = useChatStore()
      const existingMessages = [
        { id: 1, content: '消息' } as any,
      ]
      store.messagesMap.set(1, existingMessages)

      const messages = store._getOrCreateMessages(1)

      expect(messages).toBe(existingMessages)
      expect(messages).toHaveLength(1)
    })

    it('_getOrCreateMessages 应该创建新消息列表如果不存在', () => {
      const store = useChatStore()

      const messages = store._getOrCreateMessages(999)

      expect(messages).toEqual([])
      expect(store.messagesMap.has(999)).toBe(true)
    })
  })

  describe('边界条件测试', () => {
    it('应该处理空的会话列表', async () => {
      const store = useChatStore()

      ;(chatApi.getConversations as Mock).mockResolvedValueOnce({ data: [] })

      await store.fetchConversations()

      expect(store.conversations).toEqual([])
    })

    it('应该处理空的消息列表', async () => {
      const store = useChatStore()

      ;(chatApi.getMessages as Mock).mockResolvedValueOnce({ data: [] })

      await store.fetchMessages(1)

      expect(store.messagesMap.get(1)).toEqual([])
    })

    it('currentStreamState getter 应该处理新对话状态', () => {
      const store = useChatStore()
      store.newConversationStreamState = {
        processingStage: 'sending',
        processingStatus: '发送中...',
        streamingContent: '',
        streamingMessageId: null,
        streamingSources: [],
        isSending: true,
        abortController: null,
        doneReceived: false,
      }

      expect(store.currentStreamState).toBe(store.newConversationStreamState)
      expect(store.isSending).toBe(true)
    })
  })
})
