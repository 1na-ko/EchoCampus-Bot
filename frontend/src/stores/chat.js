import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getConversations, createConversation as apiCreateConversation } from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref([])
  const currentConversationId = ref(null)
  const messages = ref([])
  const loading = ref(false)

  // 加载会话列表
  async function loadConversations() {
    try {
      const data = await getConversations({ page: 1, size: 50 })
      conversations.value = data
    } catch (error) {
      console.error('加载会话列表失败:', error)
    }
  }

  // 创建新会话
  async function createConversation(title = '新对话') {
    try {
      const newConv = await apiCreateConversation(title)
      conversations.value.unshift(newConv)
      currentConversationId.value = newConv.id
      messages.value = []
      return newConv
    } catch (error) {
      console.error('创建会话失败:', error)
      throw error
    }
  }

  // 选择会话
  function selectConversation(conversationId) {
    currentConversationId.value = conversationId
  }

  // 添加消息到当前会话
  function addMessage(message) {
    messages.value.push(message)
  }

  // 清空当前消息
  function clearMessages() {
    messages.value = []
  }

  return {
    conversations,
    currentConversationId,
    messages,
    loading,
    loadConversations,
    createConversation,
    selectConversation,
    addMessage,
    clearMessages
  }
})
