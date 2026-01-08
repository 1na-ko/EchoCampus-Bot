<template>
  <div class="chat-container">
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <a-button type="primary" size="large" block @click="handleNewChat">
          <PlusOutlined /> 新对话
        </a-button>
      </div>

      <div class="conversation-list">
        <a-spin :spinning="chatStore.isLoading">
          <div
            v-for="conv in chatStore.conversations"
            :key="conv.id"
            :class="['conversation-item', { active: currentConvId === conv.id }]"
            @click="selectConversation(conv.id)"
          >
            <div class="conversation-content">
              <div class="conversation-title">{{ conv.title }}</div>
              <div class="conversation-meta">
                {{ conv.messageCount }} 条消息 · {{ formatTime(conv.updatedAt) }}
              </div>
            </div>
            <a-dropdown :trigger="['click']">
              <MoreOutlined class="conversation-more" @click.stop />
              <template #overlay>
                <a-menu>
                  <a-menu-item @click="handleRename(conv)">
                    <EditOutlined /> 重命名
                  </a-menu-item>
                  <a-menu-item danger @click="handleDelete(conv.id)">
                    <DeleteOutlined /> 删除
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>

          <a-empty
            v-if="chatStore.conversations.length === 0 && !chatStore.isLoading"
            description="暂无对话记录"
            :image="Empty.PRESENTED_IMAGE_SIMPLE"
          />
        </a-spin>
      </div>
    </div>

    <div class="chat-main">
      <div class="chat-content">
        <div v-if="!currentConvId && chatStore.messages.length === 0" class="chat-welcome">
          <div class="welcome-content">
            <div class="welcome-icon">🤖</div>
            <h1 class="welcome-title">EchoCampus Bot</h1>
            <p class="welcome-subtitle">我是您的智能IT知识助手，有什么可以帮您？</p>
          </div>
        </div>
        <div ref="messagesContainer" class="messages-container">
          <div
            v-for="msg in chatStore.messages"
            :key="msg.id"
            :class="['message-item', msg.senderType.toLowerCase()]"
          >
            <div class="message-avatar">
              <UserOutlined v-if="msg.senderType === 'USER'" />
              <RobotOutlined v-else />
            </div>
            <div class="message-content">
              <div class="message-text" v-html="renderMarkdown(msg.content)"></div>
              
              <!-- AI回复的知识来源 -->
              <div v-if="msg.senderType === 'BOT' && msg.metadata?.sources?.length" class="message-sources">
                <a-collapse ghost>
                  <a-collapse-panel key="1">
                    <template #header>
                      <div class="sources-header">
                        <DatabaseOutlined class="sources-icon" />
                        <span>知识来源 ({{ msg.metadata.sources.length }})</span>
                      </div>
                    </template>
                    <div
                      v-for="(source, idx) in msg.metadata.sources"
                      :key="idx"
                      class="source-item"
                    >
                      <div class="source-header">
                        <span class="source-title">{{ source.title }}</span>
                        <a-tag color="blue">相似度: {{ ((source.similarity || 0) * 100).toFixed(1) }}%</a-tag>
                      </div>
                      <div class="source-content">{{ source.content }}</div>
                    </div>
                  </a-collapse-panel>
                </a-collapse>
              </div>

              <div class="message-time">{{ formatTime(msg.createdAt) }}</div>
            </div>
          </div>

          <!-- 流式响应展示 -->
          <div v-if="chatStore.isSending" class="message-item bot">
            <div class="message-avatar">
              <RobotOutlined />
            </div>
            <div class="message-content">
              <!-- 动态状态显示 -->
              <div v-if="chatStore.processingStage !== 'generating' || !chatStore.streamingContent" class="message-status">
                <LoadingOutlined spin />
                <span>{{ currentStatusText }}</span>
              </div>
              
              <!-- 流式内容显示 -->
              <div v-if="chatStore.streamingContent" class="message-text streaming" v-html="renderMarkdown(chatStore.streamingContent)"></div>
              
              <!-- 知识来源预览（在整个流式过程中保持显示） -->
              <div v-if="chatStore.streamingSources.length > 0" class="message-sources preview">
                <a-collapse ghost>
                  <a-collapse-panel key="1">
                    <template #header>
                      <div class="sources-header">
                        <DatabaseOutlined class="sources-icon" />
                        <span>知识来源 ({{ chatStore.streamingSources.length }})</span>
                      </div>
                    </template>
                    <div
                      v-for="(source, idx) in chatStore.streamingSources"
                      :key="idx"
                      class="source-item"
                    >
                      <div class="source-header">
                        <span class="source-title">{{ source.title }}</span>
                      </div>
                      <div class="source-content">{{ source.content }}</div>
                    </div>
                  </a-collapse-panel>
                </a-collapse>
              </div>
              
              <!-- 打字指示器 -->
              <div v-if="chatStore.processingStage === 'generating'" class="typing-indicator">
                <span></span>
                <span></span>
                <span></span>
              </div>
            </div>
          </div>
        </div>

        <div class="input-container">
          <a-textarea
            v-model:value="inputMessage"
            :auto-size="{ minRows: 1, maxRows: 4 }"
            placeholder="输入您的问题..."
            :disabled="chatStore.isSending"
            @pressEnter="handleSendMessage"
            class="message-input"
          />
          <a-button
            type="primary"
            size="large"
            :loading="chatStore.isSending"
            :disabled="!inputMessage.trim()"
            @click="handleSendMessage"
            class="send-button"
          >
            <SendOutlined />
          </a-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, computed, watch, h } from 'vue'
import { Modal, Empty } from 'ant-design-vue'
import { useChatStore } from '@/stores/chat'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import {
  PlusOutlined,
  SendOutlined,
  UserOutlined,
  RobotOutlined,
  MoreOutlined,
  EditOutlined,
  DeleteOutlined,
  LoadingOutlined,
  DatabaseOutlined,
} from '@ant-design/icons-vue'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const chatStore = useChatStore()

const currentConvId = ref<number | undefined>()
const inputMessage = ref('')
const messagesContainer = ref<HTMLElement>()

// 根据处理阶段动态计算状态文本
const currentStatusText = computed(() => {
  if (chatStore.processingStatus) {
    return chatStore.processingStatus
  }
  
  switch (chatStore.processingStage) {
    case 'sending':
      return '正在发送...'
    case 'retrieving':
      return '正在检索相关知识...'
    case 'generating':
      return '正在生成回答...'
    case 'error':
      return '处理出错'
    default:
      return '正在处理...'
  }
})

// 配置 marked
marked.setOptions({
  highlight: (code, lang) => {
    const language = hljs.getLanguage(lang) ? lang : 'plaintext'
    return hljs.highlight(code, { language }).value
  },
  breaks: true,
})

const renderMarkdown = (content: string) => {
  return marked(content)
}

const formatTime = (time: string) => {
  return dayjs(time).fromNow()
}

const handleNewChat = () => {
  currentConvId.value = undefined
  chatStore.clearCurrentConversation()
}

const selectConversation = async (id: number) => {
  currentConvId.value = id
  await chatStore.selectConversation(id)
  scrollToBottom()
}

const handleSendMessage = async (e?: Event) => {
  if (e) {
    e.preventDefault()
  }
  
  const message = inputMessage.value.trim()
  if (!message || chatStore.isSending) return

  inputMessage.value = ''
  
  // 使用流式API发送消息
  await chatStore.sendMessageStream(message, currentConvId.value)
  
  // 如果是新会话，更新当前会话ID
  if (!currentConvId.value && chatStore.currentConversation) {
    currentConvId.value = chatStore.currentConversation.id
  }

  await nextTick()
  scrollToBottom()
}

// 监听流式内容变化，自动滚动
watch(() => chatStore.streamingContent, () => {
  scrollToBottom()
})

// 监听消息列表变化，自动滚动
watch(() => chatStore.messages.length, () => {
  scrollToBottom()
})

const sendQuickQuestion = (question: string) => {
  inputMessage.value = question
  handleSendMessage()
}

const handleRename = (conv: any) => {
  Modal.confirm({
    title: '重命名对话',
    content: h('div', [
      h('input', {
        id: 'rename-input',
        value: conv.title,
        class: 'ant-input',
        style: { width: '100%', marginTop: '12px' },
      }),
    ]),
    onOk: async () => {
      const input = document.getElementById('rename-input') as HTMLInputElement
      const newTitle = input?.value.trim()
      if (newTitle) {
        await chatStore.updateConversationTitle(conv.id, newTitle)
      }
    },
  })
}

const handleDelete = (id: number) => {
  Modal.confirm({
    title: '确认删除',
    content: '确定要删除这个对话吗？此操作无法撤销。',
    okText: '删除',
    okType: 'danger',
    onOk: async () => {
      await chatStore.deleteConversation(id)
      if (currentConvId.value === id) {
        handleNewChat()
      }
    },
  })
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

onMounted(() => {
  chatStore.fetchConversations()
})

onUnmounted(() => {
  // 取消正在进行的流式请求
  chatStore.cancelStream()
})
</script>

<style scoped>
.chat-container {
  display: flex;
  height: calc(100vh - 112px);
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.chat-sidebar {
  width: 280px;
  border-right: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conversation-item {
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.conversation-item:hover {
  background: #f5f5f5;
}

.conversation-item.active {
  background: #e6f7ff;
  border-left: 3px solid #1890ff;
}

.conversation-content {
  flex: 1;
  min-width: 0;
}

.conversation-title {
  font-weight: 500;
  color: #1a1a1a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-meta {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.conversation-more {
  font-size: 16px;
  color: #999;
  padding: 4px;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  position: relative;
}

.chat-welcome {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 40px 20px;
}

.welcome-content {
  text-align: center;
  max-width: 600px;
}

.welcome-icon {
  font-size: 80px;
  margin-bottom: 24px;
}

.welcome-title {
  font-size: 36px;
  font-weight: 700;
  margin-bottom: 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.welcome-subtitle {
  font-size: 16px;
  color: #666;
  margin-bottom: 20px;
}

.chat-content {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.message-item.bot {
  justify-content: flex-start;
}

.message-item.user {
  justify-content: flex-end;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.message-item.bot .message-avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.message-item.user .message-avatar {
  background: #1890ff;
  color: white;
  order: 2;
}

.message-content {
  max-width: 70%;
  min-width: 100px;
}

.message-item.user .message-content {
  order: 1;
}

.message-text {
  padding: 14px 18px;
  border-radius: 12px;
  line-height: 1.6;
  word-break: break-word;
}

.message-item.bot .message-text {
  background: #f5f5f5;
  border-top-left-radius: 4px;
}

.message-item.user .message-text {
  background: #1890ff;
  color: white;
  border-top-right-radius: 4px;
}

.message-text :deep(p) {
  margin: 8px 0;
}

.message-text :deep(p:first-child) {
  margin-top: 0;
}

.message-text :deep(p:last-child) {
  margin-bottom: 0;
}

.message-text :deep(pre) {
  background: #282c34;
  color: #abb2bf;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 8px 0;
}

.message-text :deep(code) {
  background: rgba(0, 0, 0, 0.05);
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
}

.message-text :deep(pre code) {
  background: none;
  padding: 0;
}

.message-sources {
  margin-top: 12px;
  border-radius: 8px;
  overflow: hidden;
}

.message-sources :deep(.ant-collapse) {
  background: transparent;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
}

.message-sources :deep(.ant-collapse-item) {
  border: none;
}

.message-sources :deep(.ant-collapse-header) {
  padding: 12px 16px !important;
  background: #fafafa;
  border-radius: 8px;
  font-weight: 500;
}

.message-sources :deep(.ant-collapse-content) {
  background: white;
  border-top: 1px solid #e8e8e8;
}

.sources-header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #1890ff;
}

.sources-icon {
  font-size: 16px;
}

.source-item {
  padding: 14px 16px;
  background: white;
  border-bottom: 1px solid #f0f0f0;
  transition: background 0.2s;
}

.source-item:last-child {
  border-bottom: none;
}

.source-item:hover {
  background: #fafafa;
}

.source-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  gap: 12px;
}

.source-title {
  font-weight: 500;
  color: #1a1a1a;
  flex: 1;
  font-size: 14px;
}

.source-content {
  font-size: 13px;
  color: #666;
  line-height: 1.6;
  padding-left: 0;
}

.message-time {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.message-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: #f0f7ff;
  border-radius: 12px;
  color: #1890ff;
  font-size: 13px;
  margin-bottom: 8px;
}

.message-text.streaming {
  animation: pulse 1s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.8;
  }
}

.message-sources.preview {
  opacity: 0.9;
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
  background: #999;
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
    opacity: 0.3;
    transform: translateY(0);
  }
  30% {
    opacity: 1;
    transform: translateY(-8px);
  }
}

.input-container {
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  gap: 12px;
  background: white;
}

.message-input {
  flex: 1;
  resize: none;
  border-radius: 8px;
}

.send-button {
  height: auto;
  border-radius: 8px;
  padding: 0 24px;
}

@media (max-width: 768px) {
  .chat-sidebar {
    display: none;
  }

  .message-content {
    max-width: 85%;
  }

  .welcome-subtitle {
    margin-bottom: 20px;
  }

  .welcome-title {
    font-size: 28px;
  }
}
</style>
