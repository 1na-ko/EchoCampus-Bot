<template>
  <div class="chat-container">
    <!-- 会话列表 -->
    <div class="conversation-list">
      <div class="list-header">
        <h3>对话历史</h3>
        <el-button type="primary" size="small" @click="handleNewChat">
          <el-icon><Plus /></el-icon>
          新对话
        </el-button>
      </div>
      
      <div class="list-content">
        <div
          v-for="conv in chatStore.conversations"
          :key="conv.id"
          class="conversation-item list-item"
          :class="{ active: conv.id === chatStore.currentConversationId }"
          @click="handleSelectConversation(conv.id)"
        >
          <div class="conv-title">{{ conv.title }}</div>
          <div class="conv-time">{{ formatTime(conv.updatedAt) }}</div>
        </div>
        
        <el-empty v-if="chatStore.conversations.length === 0" description="暂无对话记录" />
      </div>
    </div>

    <!-- 聊天区域 -->
    <div class="chat-area">
      <!-- 消息列表 -->
      <div ref="messageListRef" class="message-list">
        <div v-if="chatStore.messages.length === 0" class="empty-state">
          <el-icon :size="64" color="#d3d3d3"><ChatDotRound /></el-icon>
          <p>开始与智能助手对话吧</p>
        </div>
        
        <div
          v-for="(msg, index) in chatStore.messages"
          :key="index"
          class="message-item"
          :class="msg.type"
        >
          <div class="message-avatar">
            <el-icon v-if="msg.type === 'user'" :size="24"><User /></el-icon>
            <el-icon v-else :size="24"><ChatDotRound /></el-icon>
          </div>
          
          <div class="message-content">
            <div class="message-text">{{ msg.content }}</div>
            
            <!-- 来源信息 -->
            <div v-if="msg.sources && msg.sources.length > 0" class="sources">
              <div class="sources-title">
                <el-icon><Document /></el-icon>
                <span>参考来源</span>
              </div>
              <div class="source-item" v-for="source in msg.sources" :key="source.docId">
                {{ source.title }}
              </div>
            </div>
            
            <div class="message-time">{{ formatTime(msg.createdAt) }}</div>
          </div>
        </div>
        
        <!-- 加载中 -->
        <div v-if="loading" class="message-item bot">
          <div class="message-avatar">
            <el-icon :size="24"><ChatDotRound /></el-icon>
          </div>
          <div class="message-content">
            <div class="typing-indicator">
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入框 -->
      <div class="input-area">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="3"
          placeholder="输入你的问题..."
          @keydown.ctrl.enter="handleSend"
        />
        <el-button
          type="primary"
          :loading="loading"
          :disabled="!inputMessage.trim()"
          @click="handleSend"
        >
          发送 (Ctrl+Enter)
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { useChatStore } from '@/stores/chat'
import { sendMessage, getMessages } from '@/api/chat'

const chatStore = useChatStore()
const inputMessage = ref('')
const loading = ref(false)
const messageListRef = ref(null)

onMounted(async () => {
  await chatStore.loadConversations()
  if (chatStore.conversations.length > 0) {
    await handleSelectConversation(chatStore.conversations[0].id)
  }
})

// 新建对话
async function handleNewChat() {
  try {
    await chatStore.createConversation()
  } catch (error) {
    ElMessage.error('创建对话失败')
  }
}

// 选择会话
async function handleSelectConversation(conversationId) {
  chatStore.selectConversation(conversationId)
  chatStore.clearMessages()
  
  try {
    const messages = await getMessages(conversationId)
    messages.forEach(msg => {
      chatStore.addMessage({
        type: msg.senderType === 'USER' ? 'user' : 'bot',
        content: msg.content,
        createdAt: msg.createdAt
      })
    })
    scrollToBottom()
  } catch (error) {
    ElMessage.error('加载消息失败')
  }
}

// 发送消息
async function handleSend() {
  if (!inputMessage.value.trim()) return
  
  const message = inputMessage.value.trim()
  inputMessage.value = ''
  
  // 添加用户消息
  chatStore.addMessage({
    type: 'user',
    content: message,
    createdAt: new Date()
  })
  scrollToBottom()
  
  loading.value = true
  
  try {
    const response = await sendMessage({
      message,
      conversationId: chatStore.currentConversationId
    })
    
    // 添加AI回复
    chatStore.addMessage({
      type: 'bot',
      content: response.answer,
      sources: response.sources,
      createdAt: response.createdAt
    })
    
    // 更新会话ID
    if (!chatStore.currentConversationId) {
      chatStore.selectConversation(response.conversationId)
      await chatStore.loadConversations()
    }
    
    scrollToBottom()
  } catch (error) {
    ElMessage.error('发送失败，请重试')
  } finally {
    loading.value = false
  }
}

// 滚动到底部
function scrollToBottom() {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

// 格式化时间
function formatTime(time) {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now - date
  
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  
  return date.toLocaleDateString()
}
</script>

<style scoped>
.chat-container {
  display: flex;
  height: 100vh;
  background: #fff;
}

.conversation-list {
  width: 260px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.list-header {
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.list-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.list-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conversation-item {
  padding: 12px;
  cursor: pointer;
  transition: background 0.2s;
}

.conversation-item:hover {
  background: #f5f7fa;
}

.conversation-item.active {
  background: #ecf5ff;
}

.conv-title {
  font-size: 14px;
  color: #303133;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  font-size: 12px;
  color: #909399;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.empty-state {
  text-align: center;
  margin-top: 100px;
  color: #909399;
}

.empty-state p {
  margin-top: 16px;
  font-size: 14px;
}

.message-item {
  display: flex;
  margin-bottom: 24px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.message-item.user .message-avatar {
  background: #409eff;
  color: #fff;
}

.message-content {
  max-width: 70%;
  margin: 0 12px;
}

.message-item.user .message-content {
  align-items: flex-end;
}

.message-text {
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-item.user .message-text {
  background: #409eff;
  color: #fff;
}

.sources {
  margin-top: 8px;
  padding: 8px 12px;
  background: #f9f9f9;
  border-radius: 4px;
  border: 1px solid #f0f0f0;
}

.sources-title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #606266;
  margin-bottom: 8px;
}

.source-item {
  font-size: 12px;
  color: #909399;
  padding: 4px 0;
}

.message-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 4px;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 12px 16px;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c0c4cc;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-10px);
    opacity: 1;
  }
}

.input-area {
  padding: 16px;
  border-top: 1px solid #e4e7ed;
  background: #fff;
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.input-area :deep(.el-textarea) {
  flex: 1;
}

.input-area :deep(.el-textarea__inner) {
  resize: none;
  border-radius: 8px;
}
</style>
