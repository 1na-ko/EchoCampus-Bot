<template>
  <div class="chat-container">
    <div :class="['chat-sidebar', { collapsed: isSidebarCollapsed }]">
      <div class="sidebar-header">
        <div class="toggle-btn-container">
          <a-button type="text" @click="toggleSidebar" class="toggle-btn">
            <template #icon>
              <MenuUnfoldOutlined v-if="isSidebarCollapsed" />
              <MenuFoldOutlined v-else />
            </template>
          </a-button>
        </div>
          
        <a-button v-if="!isSidebarCollapsed" type="primary" size="large" block @click="handleNewChat">
          <PlusOutlined /> 新对话
        </a-button>
        <a-tooltip v-else placement="right" title="新对话">
           <a-button type="primary" shape="circle" size="large" @click="handleNewChat" class="collapsed-new-chat-btn">
            <template #icon><PlusOutlined /></template>
          </a-button>
        </a-tooltip>
      </div>

      <div class="conversation-list" :class="{ 'collapsed-list': isSidebarCollapsed }">
        <a-spin :spinning="chatStore.isLoading">
          <div
            v-for="conv in chatStore.conversations"
            :key="conv.id"
            :class="['conversation-item', { active: currentConvId === conv.id, 'collapsed-item': isSidebarCollapsed }]"
            @click="selectConversation(conv.id)"
          >
            <template v-if="!isSidebarCollapsed">
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
            </template>
            
            <template v-else>
              <a-tooltip placement="right">
                <template #title>
                  <div>
                    <div>{{ conv.title }}</div>
                    <div style="font-size: 12px; margin-top: 4px; opacity: 0.8">{{ formatTime(conv.updatedAt) }}</div>
                  </div>
                </template>
                <div class="conversation-mini-avatar">
                  {{ conv.title.substring(0, 1) }}
                </div>
              </a-tooltip>
            </template>
          </div>

          <a-empty
            v-if="chatStore.conversations.length === 0 && !chatStore.isLoading && !isSidebarCollapsed"
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
            <div class="welcome-icon">
              <RobotOutlined :style="{ fontSize: '64px', color: 'var(--primary-color)' }" />
            </div>
            <h1 class="welcome-title">EchoCampus Bot</h1>
            <p class="welcome-subtitle">我是您的智能校园问答助手，有什么可以帮您？</p>
          </div>
        </div>
        <div ref="messagesContainer" class="messages-container">
          <TransitionGroup name="message-list">
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
                          <span class="similarity-tag">相似度: {{ ((source.similarity || 0) * 100).toFixed(1) }}%</span>
                        </div>
                        <div class="source-content">{{ source.content }}</div>
                      </div>
                    </a-collapse-panel>
                  </a-collapse>
                </div>

                <div class="message-time">{{ formatTime(msg.createdAt) }}</div>
              </div>
            </div>
          </TransitionGroup>

          <!-- 流式响应展示 -->
          <div v-if="chatStore.isSending" class="message-item bot">
            <div class="message-avatar">
              <RobotOutlined />
            </div>
            <div class="message-content">
              <!-- 动态状态显示，回答过程中可以一直保留 -->
              <div v-if="chatStore.isSending" class="message-status">
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
          <div 
            ref="inputWrapperRef"
            class="fancy-input-wrapper"
            :class="{ 'focused': isInputFocused }"
            @mousemove="handleInputMouseMove"
            :style="{ '--mouse-x': `${mouseX}px`, '--mouse-y': `${mouseY}px` }"
          >
            <!-- Background Glow -->
            <div class="input-glow"></div>
            
            <!-- Particles (Always show for ambient effect) -->
            <div class="particles-container">
              <span v-for="i in 8" :key="i" class="particle"></span>
            </div>

            <a-textarea
              v-model:value="inputMessage"
              :auto-size="{ minRows: 1, maxRows: 4 }"
              placeholder="输入您的问题..."
              @pressEnter="handleSendMessage"
              @focus="isInputFocused = true"
              @blur="isInputFocused = false"
              class="message-input"
            />
          </div>
          
          <a-button
            type="primary"
            size="large"
            :disabled="chatStore.isSending || !inputMessage.trim()"
            @click="handleSendMessage"
            class="send-button"
          >
            <transition name="icon-slide">
              <span v-if="chatStore.isSending" class="btn-icon-wrapper">
                <LoadingOutlined spin />
              </span>
              <span v-else class="btn-icon-wrapper">
                <SendOutlined />
              </span>
            </transition>
          </a-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, computed, watch, h, ref, onMounted, onUnmounted } from 'vue'
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
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons-vue'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const chatStore = useChatStore()
const isSidebarCollapsed = ref(false)

const currentConvId = ref<number | undefined>()
const inputMessage = ref('')
const messagesContainer = ref<HTMLElement>()

// 监听 store 中 currentConversation 的变化，同步更新本地 currentConvId
// 这是解决新对话创建后第二次提问产生新对话问题的关键
watch(
  () => chatStore.currentConversation?.id,
  (newId) => {
    if (newId && newId !== currentConvId.value) {
      currentConvId.value = newId
    }
  }
)

// Input fancy effects
const isInputFocused = ref(false)
const inputWrapperRef = ref<HTMLElement>()
const mouseX = ref(0)
const mouseY = ref(0)

const handleInputMouseMove = (e: MouseEvent) => {
  if (!inputWrapperRef.value) return
  const rect = inputWrapperRef.value.getBoundingClientRect()
  mouseX.value = e.clientX - rect.left
  mouseY.value = e.clientY - rect.top
}

const toggleSidebar = () => {
  isSidebarCollapsed.value = !isSidebarCollapsed.value
}

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
  // Allow Shift+Enter for newlines
  if (e instanceof KeyboardEvent && e.shiftKey) {
    return
  }

  if (e) {
    e.preventDefault()
  }
  
  const message = inputMessage.value.trim()
  if (!message || chatStore.isSending) return

  // Store message and clear input immediately
  inputMessage.value = ''
  
  // Ensure the UI updates the cleared value before we start processing
  await nextTick()
  
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
  // 不取消流式请求，让后台对话继续生成
  // 如果确实需要取消当前对话的请求，可以使用：
  // if (currentConvId.value) {
  //   chatStore.cancelStream(currentConvId.value)
  // }
})
</script>

<style scoped>
.chat-container {
  display: flex;
  /* MainLayout header is 64px, margins are 24px top/bottom. 
     We want to fit exactly in the content area which is already padded by MainLayout.
     But wait, MainLayout adds 24px margin around layout-content.
     So available height is calc(100vh - 64px - 48px).
     However, we also have margin: 16px in this component.
     It's better to force absolute fill or use 100% of parent if parent is constrained.
     But parent (layout-content) usually grows with content.
     To fix overflow, we need to set a fixed height relative to viewport.
     Header: 64px. Layout Patting: 24px top, 24px bottom.
     Total occupied vertical space outside char container: 64 + 24 + 24 = 112px.
     So height should be calc(100vh - 112px).
     We also had margin: 16px on .chat-container.
     Ideally we remove margin on chat-container and let MainLayout handle spacing, OR
     we adjust the calculation.
     Let's use calc(100vh - 64px - 48px) = calc(100vh - 112px) if we remove this component's margin.
  */
  height: calc(100vh - 112px);
  /* margin: 16px; -> Removing margin to rely on Layout padding */
  background: var(--surface-color);
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--border-light);
}

.chat-sidebar {
  width: 280px;
  border-right: 1px solid var(--border-light);
  display: flex;
  flex-direction: column;
  background: var(--bg-secondary);
  transition: width 0.3s cubic-bezier(0.2, 0, 0, 1);
}

.chat-sidebar.collapsed {
  width: 80px;
}

.sidebar-header {
  padding: 20px;
  background: transparent;
  display: flex;
  flex-direction: column;
  gap: 16px;
  /* Ensure children are centered when needed */
  align-items: stretch; 
}

.chat-sidebar.collapsed .sidebar-header {
  align-items: center;
  padding: 20px 0; /* reduce padding in collapsed mode */
}

.collapsed-new-chat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto; /* fallback */
}

.toggle-btn-container {
  display: flex;
  justify-content: flex-end;
  width: 100%;
} 

.chat-sidebar.collapsed .toggle-btn-container {
  justify-content: center;
}

/* Animations */
.message-list-enter-active,
.message-list-leave-active {
  transition: all 0.4s ease;
}

.message-list-enter-from {
  opacity: 0;
  transform: translateY(20px);
}

.message-list-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px 12px;
}

.conversation-list.collapsed-list {
  padding: 0 8px 12px;
}

.conversation-item {
  padding: 12px 16px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid transparent;
  min-height: 66px; /* Ensure consistent height for items */
}

.conversation-item.collapsed-item {
  padding: 0;
  justify-content: center;
  background: transparent;
  width: 100%;
}

.conversation-item:not(.collapsed-item):hover {
  background: var(--bg-secondary);
  box-shadow: var(--shadow-sm);
  transform: translateX(2px);
  z-index: 1;
}

/* Specific hover effect for collapsed mode item wrapper? No, let the avatar handle it or the item itself */
.conversation-item.collapsed-item:hover .conversation-mini-avatar {
  background: var(--bg-secondary);
  border-color: var(--primary-color); 
  color: var(--primary-color);
  /* Subtler hover: no scale, just color shift */
}

.conversation-mini-avatar {
  width: 48px;
  height: 48px;
  background: var(--surface-color);
  border: 1px solid var(--border-light);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-secondary);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
}

.conversation-item.active .conversation-mini-avatar {
  background: var(--primary-color);
  color: white;
  border-color: var(--primary-color);
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.3);
}

.conversation-item.active {
  background: var(--surface-color);
  border-color: var(--border-light);
  box-shadow: var(--shadow-sm);
  /* border-left: none; */
}

/* Reset active style for collapsed mode to avoid box style conflict */
.conversation-item.active.collapsed-item {
  background: transparent;
  border-color: transparent;
  box-shadow: none;
}

.conversation-content {
  flex: 1;
  min-width: 0;
}

.conversation-title {
  font-weight: 500;
  color: var(--text-primary);
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-meta {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 4px;
}

.conversation-more {
  font-size: 16px;
  color: var(--text-tertiary);
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
}

.conversation-more:hover {
  background: rgba(0,0,0,0.05);
  color: var(--text-primary);
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  position: relative;
  background: var(--surface-color);
}

.chat-welcome {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 40px;
}

.welcome-content {
  text-align: center;
  max-width: 600px;
  padding: 48px;
  background: var(--surface-color);
  border-radius: var(--radius-lg);
}

.welcome-icon {
  font-size: 64px;
  margin-bottom: 24px;
  display: inline-block;
  animation: float 6s ease-in-out infinite;
}

@keyframes float {
  0% { transform: translateY(0px); }
  50% { transform: translateY(-10px); }
  100% { transform: translateY(0px); }
}

.welcome-title {
  font-size: 32px;
  font-weight: 800;
  margin-bottom: 12px;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -1px;
}

.welcome-subtitle {
  font-size: 16px;
  color: var(--text-secondary);
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
  padding: 24px 10%; /* More centralized reading area */
}

.message-item {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
  animation: slideUp 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.message-item.bot {
  justify-content: flex-start;
}

.message-item.user {
  justify-content: flex-end;
}

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
  box-shadow: var(--shadow-sm);
}

.message-item.bot .message-avatar {
  background: linear-gradient(135deg, #e0e7ff 0%, #ede9fe 100%);
  color: var(--primary-color);
  border: 1px solid #c7d2fe;
}

.message-item.user .message-avatar {
  background: var(--bg-secondary);
  color: var(--text-secondary);
  order: 2;
  display: none; /* Hide user avatar for cleaner look */
}

.message-content {
  max-width: 80%;
  min-width: 100px;
}

.message-item.user .message-content {
  order: 1;
  max-width: 80%;
}

.message-text {
  padding: 16px 20px;
  border-radius: var(--radius-lg);
  line-height: 1.6;
  word-break: break-word;
  font-size: 15px;
  box-shadow: var(--shadow-sm);
}

.message-item.bot .message-text {
  background: var(--bg-secondary);
  color: var(--text-primary);
  border-top-left-radius: 2px;
  border: 1px solid var(--border-light);
}

.message-item.user .message-text {
  background: var(--primary-color);
  color: white;
  border-top-right-radius: 2px;
  box-shadow: 0 4px 6px -1px rgba(99, 102, 241, 0.2);
}

.message-text :deep(p) {
  margin: 0.5em 0;
}

.message-text :deep(p:first-child) {
  margin-top: 0;
}

.message-text :deep(p:last-child) {
  margin-bottom: 0;
}

/* Markdown标题样式 */
.message-text :deep(h1),
.message-text :deep(h2),
.message-text :deep(h3),
.message-text :deep(h4),
.message-text :deep(h5),
.message-text :deep(h6) {
  margin-top: 1.2em;
  margin-bottom: 0.6em;
  padding-left: 0;
  margin-left: 0;
  font-weight: 600;
  line-height: 1.35;
  color: currentColor; /* 跟随父元素颜色 */
}

/* 统一左对齐，但通过字号和间距体现层级 */
.message-text :deep(h1) {
  font-size: 1.6em; /* 稍微调小，适应对话框 */
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
  padding-bottom: 0.3em;
}

.message-item.user .message-text :deep(h1) {
  border-bottom-color: rgba(255, 255, 255, 0.2);
}

.message-text :deep(h2) {
  font-size: 1.4em;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  padding-bottom: 0.3em;
}

.message-item.user .message-text :deep(h2) {
  border-bottom-color: rgba(255, 255, 255, 0.15);
}

.message-text :deep(h3) {
  font-size: 1.25em;
}

.message-text :deep(h4) {
  font-size: 1.15em;
}

.message-text :deep(h5) {
  font-size: 1.05em;
  font-weight: bold;
}

.message-text :deep(h6) {
  font-size: 1em;
  color: var(--text-secondary);
  text-transform: uppercase;
}

.message-item.user .message-text :deep(h6) {
  color: rgba(255, 255, 255, 0.8);
}

.message-text :deep(h1:first-child),
.message-text :deep(h2:first-child),
.message-text :deep(h3:first-child),
.message-text :deep(h4:first-child),
.message-text :deep(h5:first-child),
.message-text :deep(h6:first-child) {
  margin-top: 0;
}

/* 列表样式 - 增加一点呼吸感 */
.message-text :deep(ul),
.message-text :deep(ol) {
  margin: 0.8em 0;
  padding-left: 1.5em; /* 标准缩进 */
}

.message-text :deep(li) {
  margin: 0.3em 0;
}

/* 引用块优化 - 更柔和 */
.message-text :deep(blockquote) {
  margin: 1em 0;
  padding: 0.5em 1em;
  border-left: 3px solid var(--primary-color);
  background: rgba(0, 0, 0, 0.02);
  border-radius: 0 4px 4px 0;
  color: var(--text-secondary);
}

.message-item.user .message-text :deep(blockquote) {
  border-left-color: rgba(255, 255, 255, 0.5);
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.9);
}

/* 表格样式支持 */
.message-text :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 1em 0;
  font-size: 0.95em;
}

.message-text :deep(th),
.message-text :deep(td) {
  border: 1px solid var(--border-light);
  padding: 8px 12px;
  text-align: left;
}

.message-text :deep(th) {
  background: rgba(0, 0, 0, 0.02);
  font-weight: 600;
}

.message-item.user .message-text :deep(th),
.message-item.user .message-text :deep(td) {
  border-color: rgba(255, 255, 255, 0.2);
}

.message-item.user .message-text :deep(th) {
  background: rgba(255, 255, 255, 0.1);
}

.message-text :deep(pre) {
  background: #1e1e1e;
  border-radius: 8px;
  margin: 12px 0;
}

.message-text :deep(code) {
  background: rgba(0, 0, 0, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
}

/* User message code block adjustment */
.message-item.user .message-text :deep(code) {
  background: rgba(255, 255, 255, 0.2);
  color: white;
}

.message-sources {
  margin-top: 12px;
  width: 100%;
}

.message-sources :deep(.ant-collapse) {
  background: var(--bg-secondary);
  border: none;
  border-radius: var(--radius-lg);
}

.message-sources :deep(.ant-collapse-item) {
  border: none;
}

.message-sources :deep(.ant-collapse-header) {
  padding: 8px 12px !important;
  background: transparent;
  font-weight: 500;
  border-radius: var(--radius-lg) !important;
  transition: background 0.2s;
}

.message-sources :deep(.ant-collapse-header:hover) {
  background: rgba(0, 0, 0, 0.02);
}

.message-sources :deep(.ant-collapse-content) {
  background: transparent;
  border-top: 1px dashed var(--border-light);
}

.message-sources :deep(.ant-collapse-content-box) {
  padding: 12px !important;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sources-header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 13px;
  user-select: none;
}

.sources-header .sources-icon {
  color: var(--primary-color);
}

.source-item {
  padding: 10px 12px;
  background: var(--surface-color);
  border-radius: var(--radius-md);
  border: 1px solid transparent;
  box-shadow: 0 1px 2px rgba(0,0,0,0.03);
  transition: all 0.2s;
  cursor: default;
  position: relative;
  overflow: hidden;
}

.source-item:hover {
  border-color: var(--primary-color);
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.1);
  /* No float */
}

/* Indicator strip removed */

.source-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 6px;
  gap: 8px;
}

.source-title {
  font-weight: 600;
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.4;
  flex: 1;
}

.source-content {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2; /* Limit to 2 lines */
  -webkit-box-orient: vertical;
  overflow: hidden;
  opacity: 0.8;
}

/* Custom Tag Style for Similarity */
.similarity-tag {
  font-size: 11px;
  padding: 1px 6px;
  background: rgba(24, 144, 255, 0.1);
  color: var(--primary-color);
  border-radius: 4px;
  white-space: nowrap;
  font-weight: 500;
}

.message-time {
  font-size: 11px;
  color: var(--text-tertiary);
  margin-top: 6px;
  text-align: right;
  opacity: 0.7;
}

.message-item.bot .message-time {
  text-align: left;
}

/* Processing indicator */
.message-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  background: var(--bg-secondary);
  border-radius: 20px;
  color: var(--primary-color);
  font-size: 12px;
  margin-bottom: 8px;
  border: 1px solid var(--border-light);
}

/* Input Area */
.input-container {
  padding: 20px 10%;
  background: transparent;
  width: 100%;
  display: flex;
  gap: 12px;
  align-items: flex-end;
  position: relative;
}

.input-container::before {
  content: '';
  position: absolute;
  top: -20px;
  left: 0;
  right: 0;
  height: 20px;
  background: linear-gradient(to top, var(--surface-color), transparent);
  pointer-events: none;
}

/* Fancy Input Wrapper */
.fancy-input-wrapper {
  position: relative;
  width: 100%;
  border-radius: var(--radius-lg);
  background: var(--surface-color);
  border: 1px solid var(--border-light);
  transition: all 0.3s;
  overflow: hidden;
}

.fancy-input-wrapper.focused {
  border-color: var(--primary-color); 
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2), 0 0 25px rgba(24, 144, 255, 0.35);
  background: linear-gradient(to top, rgba(24, 144, 255, 0.05), var(--surface-color)); /* Subtle gradient lift */
}

/* Ambient Pulse for Non-focused state */
.fancy-input-wrapper:not(.focused):not(:hover) {
  border-color: rgba(24, 144, 255, 0.2); /* visible colored border */
  box-shadow: 0 0 10px rgba(24, 144, 255, 0.05); /* base glow */
  animation: ambient-pulse 3s infinite ease-in-out; /* faster pulse */
}

@keyframes ambient-pulse {
  0% { box-shadow: 0 0 0 0 rgba(24, 144, 255, 0.1); border-color: rgba(24, 144, 255, 0.2); }
  50% { box-shadow: 0 0 20px rgba(24, 144, 255, 0.25); border-color: rgba(24, 144, 255, 0.5); }
  100% { box-shadow: 0 0 0 0 rgba(24, 144, 255, 0.1); border-color: rgba(24, 144, 255, 0.2); }
}

/* Dynamic Glow Effect */
.input-glow {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  background: radial-gradient(
    800px circle at var(--mouse-x) var(--mouse-y), 
    rgba(24, 144, 255, 0.15), 
    transparent 45%
  );
  opacity: 0; /* Hidden by default so it disappears when mouse leaves */
  transition: opacity 0.3s, background 0.2s;
  z-index: 0;
}

.fancy-input-wrapper:hover .input-glow {
  opacity: 0.7;
  background: radial-gradient(
    600px circle at var(--mouse-x) var(--mouse-y), 
    rgba(24, 144, 255, 0.2), 
    transparent 50%
  );
}

/* Only show strong tracked glow if focused AND hovered */
.fancy-input-wrapper.focused:hover .input-glow {
  opacity: 1;
  background: radial-gradient(
    600px circle at var(--mouse-x) var(--mouse-y), 
    rgba(24, 144, 255, 0.25), 
    transparent 50%
  );
}

/* Particles */
.particles-container {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  overflow: hidden;
  pointer-events: none;
  z-index: 1;
}

.particle {
  position: absolute;
  bottom: -10px;
  width: 6px;
  height: 6px;
  background: var(--primary-color);
  border-radius: 50%;
  opacity: 0;
  box-shadow: 0 0 10px var(--primary-color); /* Glowing particles */
  animation: float-particle 2.5s infinite linear;
}

/* Ambient Particles (when not focused) */
.fancy-input-wrapper:not(.focused) .particle:nth-child(even) {
  opacity: 0.4; 
  animation-duration: 4s;
}
.fancy-input-wrapper:not(.focused) .particle:nth-child(odd) {
  opacity: 0.2;
  animation-duration: 6s;
}

/* Focused Particles */
.fancy-input-wrapper.focused .particle {
  opacity: 0.9;
  filter: drop-shadow(0 0 5px var(--primary-color));
  animation-duration: 2s; /* Faster rise */
}

.particle:nth-child(1) { left: 10%; animation-delay: 0s; width: 3px; height: 3px; }
.particle:nth-child(2) { left: 30%; animation-delay: 1.2s; width: 5px; height: 5px; }
.particle:nth-child(3) { left: 50%; animation-delay: 0.5s; width: 2px; height: 2px; }
.particle:nth-child(4) { left: 70%; animation-delay: 1.8s; width: 4px; height: 4px; }
.particle:nth-child(5) { left: 90%; animation-delay: 0.8s; width: 3px; height: 3px; }
.particle:nth-child(6) { left: 20%; animation-delay: 2.2s; }
.particle:nth-child(7) { left: 60%; animation-delay: 1.5s; width: 4px; height: 4px; }
.particle:nth-child(8) { left: 80%; animation-delay: 2.8s; width: 2px; height: 2px; }

@keyframes float-particle {
  0% { transform: translateY(0) scale(1); opacity: 0; }
  20% { opacity: 0.6; }
  80% { opacity: 0.2; }
  100% { transform: translateY(-60px) scale(0); opacity: 0; }
}

/* Override Ant Design Textarea styles */
.message-input {
  border-radius: var(--radius-lg) !important;
  border: none !important;
  box-shadow: none !important;
  padding: 12px 16px;
  font-size: 15px;
  transition: all 0.3s;
  background: transparent !important;
  position: relative;
  z-index: 2;
}

.message-input:focus {
  box-shadow: none !important;
}

.send-button {
  width: 48px !important;
  height: 48px !important;
  border-radius: 50% !important;
  display: flex !important; /* Force flex */
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: var(--shadow-md);
  padding: 0 !important; /* Reset padding to ensure centering */
  position: relative;
  overflow: hidden;
}

.btn-icon-wrapper {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%); /* Correct centering */
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

/* Icon Slide Transition */
.icon-slide-enter-active,
.icon-slide-leave-active {
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1); /* Nice bounce or smooth ease */
}

/* Enter: From Left */
.icon-slide-enter-from {
  opacity: 0;
  transform: translate(-150%, -50%);
}

/* Leave: To Right */
.icon-slide-leave-to {
  opacity: 0;
  transform: translate(50%, -50%);
}

/* Ensure resting state is centered */
/* Classes like enter-to and leave-from default to the element's actual style which is centered */

@media (max-width: 768px) {
  .chat-container {
    height: 100vh;
    margin: 0;
    border-radius: 0;
    border: none;
  }
  
  .chat-sidebar {
    display: none;
  }

  .messages-container {
    padding: 20px;
  }

  .message-content {
    max-width: 90%;
  }

  .input-container {
    padding: 16px;
  }
}
</style>
