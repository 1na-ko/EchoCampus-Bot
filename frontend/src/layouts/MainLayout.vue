<template>
  <a-layout class="main-layout">
    <a-layout-sider
      v-model:collapsed="collapsed"
      :trigger="null"
      collapsible
      :width="260"
      :collapsed-width="isMobile ? 0 : 80"
      :class="{ 'mobile-sider': isMobile, 'show': showMobileSider }"
      class="layout-sider"
    >
      <div class="logo">
        <div class="logo-icon"><RobotOutlined /></div>
        <span class="logo-text">EchoCampus</span>
      </div>

      <a-menu
        v-model:selectedKeys="selectedKeys"
        theme="dark"
        mode="inline"
        @click="handleMenuClick"
      >
        <a-menu-item key="/chat">
          <MessageOutlined />
          <span class="nav-text">智能问答</span>
        </a-menu-item>
        <a-menu-item v-if="isAdmin" key="/knowledge">
          <DatabaseOutlined />
          <span class="nav-text">知识库</span>
        </a-menu-item>
        <a-menu-item key="/profile">
          <UserOutlined />
          <span class="nav-text">个人中心</span>
        </a-menu-item>
      </a-menu>

      <div class="sider-footer">
        <a-button
          type="text"
          danger
          block
          @click="handleLogout"
          class="logout-btn"
        >
          <LogoutOutlined />
          <span class="nav-text">退出登录</span>
        </a-button>
      </div>
    </a-layout-sider>

    <a-layout>
      <a-layout-header class="layout-header">
        <MenuUnfoldOutlined
          v-if="collapsed || isMobile"
          class="trigger"
          @click="toggleSider"
        />
        <MenuFoldOutlined v-else class="trigger" @click="toggleSider" />

        <div class="header-right">
          <span class="user-name">{{ userStore.user?.nickname || userStore.user?.username }}</span>
        </div>
      </a-layout-header>

      <a-layout-content class="layout-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </a-layout-content>
    </a-layout>

    <!-- Mobile overlay -->
    <div
      v-if="isMobile && showMobileSider"
      class="mobile-overlay"
      @click="showMobileSider = false"
    ></div>
  </a-layout>
</template>

<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import { useBreakpoints } from '@vueuse/core'
import {
  MenuUnfoldOutlined,
  MenuFoldOutlined,
  MessageOutlined,
  DatabaseOutlined,
  UserOutlined,
  LogoutOutlined,
  RobotOutlined,
} from '@ant-design/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const chatStore = useChatStore()

const collapsed = ref(false)
const showMobileSider = ref(false)
const selectedKeys = ref([route.path])

const breakpoints = useBreakpoints({
  mobile: 768,
  tablet: 1024,
})

const isMobile = breakpoints.smaller('mobile')

// 判断是否为管理员
const isAdmin = computed(() => {
  return userStore.user?.role === 'ADMIN'
})

watch(
  () => route.path,
  (newPath) => {
    selectedKeys.value = [newPath]
  }
)

watch(isMobile, (newVal) => {
  if (newVal) {
    collapsed.value = true
  } else {
    showMobileSider.value = false
  }
})

const toggleSider = () => {
  if (isMobile.value) {
    showMobileSider.value = !showMobileSider.value
  } else {
    collapsed.value = !collapsed.value
  }
}

const handleMenuClick = ({ key }: { key: string | number }) => {
  router.push(String(key))
  if (isMobile.value) {
    showMobileSider.value = false
  }
}

const handleLogout = () => {
  // 清理所有状态
  userStore.clearAll()
  chatStore.clearAll()
  router.push('/login')
}

onMounted(() => {
  if (!userStore.user) {
    userStore.fetchCurrentUser()
  }
})
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
}

.layout-sider {
  height: 100vh;
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 100;
  background: #1e293b; /* Slate 800 - More modern than pure black */
  box-shadow: 4px 0 24px 0 rgba(0, 0, 0, 0.1);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1) !important; /* Smooth easing */
}

/* Logo Area */
.logo {
  height: 80px; /* Taller for elegance */
  margin: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  transition: all 0.3s;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05); /* Subtle separator */
}

.logo-icon {
  font-size: 28px;
  color: #38bdf8; /* Sky 400 - vibrant accent */
  display: flex;
  align-items: center;
  justify-content: center;
  filter: drop-shadow(0 0 8px rgba(56, 189, 248, 0.3)); /* Glow effect */
}

.logo-text {
  display: inline-block; /* Essential for width/max-width to work */
  color: #f1f5f9;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.5px;
  white-space: nowrap;
  margin-left: 12px;
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
  background: linear-gradient(135deg, #fff 0%, #cbd5e1 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  
  /* Smooth Collapse Animation */
  opacity: 1;
  max-width: 200px; /* Arbitrary large width */
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

/* Hide Logo Text when collapsed */
.layout-sider.ant-layout-sider-collapsed .logo-text {
  display: inline-block;
  opacity: 0;
  max-width: 0;
  margin-left: 0;
}

/* Menu Customization */
:deep(.ant-menu) {
  background: transparent !important;
  border-right: none !important;
  padding: 16px 12px;
}

:deep(.ant-menu-item) {
  margin-bottom: 8px !important;
  width: 100% !important;
  border-radius: 12px !important; /* Softer rounded corners */
  display: flex !important;
  align-items: center !important;
  justify-content: flex-start !important; /* Align left when expanded */
  height: 48px !important; /* Match collapsed height */
  color: #94a3b8 !important; /* Muted text */
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1) !important;
  padding-left: 20px !important; /* More breathing room on left */
  padding-right: 12px !important;
}

/* Ensure title content allows centering in expanded state */
:deep(.ant-menu-item .ant-menu-title-content) {
  flex: 1 !important; 
  display: flex !important;
  align-items: center !important;
  justify-content: flex-start !important; /* Align left */
}

/* Nav Text Animation */
.nav-text {
  display: inline-block; /* Essential for width/max-width to work */
  margin-left: 10px;
  opacity: 1;
  max-width: 200px;
  white-space: nowrap;
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

:deep(.ant-menu-inline-collapsed) .nav-text {
  opacity: 0;
  max-width: 0;
  margin-left: 0 !important;
}

:deep(.ant-menu-item:hover) {
  color: #e2e8f0 !important;
  background-color: rgba(255, 255, 255, 0.05) !important;
  /* transform: translateX(4px); */ /* Removed as requested */
}

:deep(.ant-menu-item-selected) {
  background: linear-gradient(90deg, #3b82f6 0%, #2563eb 100%) !important; /* Brand gradient */
  color: #ffffff !important;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.3); /* Soft shadow for depth */
}

/* Collapsed State Fixes - Unified Logic */
:deep(.ant-menu-inline-collapsed) {
  width: 80px;
  padding: 16px 8px; /* Reduce horizontal padding when collapsed */
}

:deep(.ant-menu-inline-collapsed .ant-menu-item) {
  padding: 0 !important;
  width: 48px !important; /* Force square width when collapsed */
  height: 48px !important; /* Force square height */
  margin: 0 auto 8px !important; /* Center locally */
  border-radius: 12px !important; /* Match border radius */
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
}

:deep(.ant-menu-inline-collapsed .ant-menu-item .ant-menu-title-content) {
  display: flex !important;
  align-items: center !important;
  justify-content: center !important; /* Force center in collapsed state */
  width: 100% !important;
  height: 100% !important;
  margin: 0 !important;
  padding: 0 !important;
  text-indent: 0 !important;
  flex: none !important; /* Override flex: 1 from expanded state */
}

/* Icon Styles */
:deep(.ant-menu-item .anticon) {
  font-size: 18px !important;
  min-width: 18px !important;
  transition: all 0.3s;
  position: relative;
  
  /* Critical Fix for Vertical Centering */
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
  line-height: 1 !important;
}

:deep(.ant-menu-item .anticon svg) {
  display: block; /* Removes any inline spacing quirks */
}

:deep(.ant-menu-inline-collapsed .ant-menu-item .anticon) {
  margin: 0 !important;
  font-size: 20px !important;
  left: auto !important; /* Reset any position adjustments */
  transform: none !important;
}

/* Flex layout helper for sider */
:deep(.ant-layout-sider-children) {
  display: flex !important;
  flex-direction: column !important;
  height: 100% !important;
}

/* Footer & Logout */
.sider-footer {
  margin-top: auto;
  padding: 24px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.logout-btn {
  color: #94a3b8;
  height: 48px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  background: rgba(0, 0, 0, 0.2);
  transition: all 0.2s ease;
  backdrop-filter: blur(10px);
  
  /* Flex Config matching Menu Items */
  display: flex !important;
  align-items: center;
  justify-content: flex-start; /* Align left */
  padding-left: 20px;
  padding-right: 12px;
}

.layout-sider.ant-layout-sider-collapsed .logout-btn {
  justify-content: center;
  padding: 0;
  width: 48px;
  margin: 0 auto;
}

/* Nav Text interaction with Logout Button specific */
.logout-btn .nav-text {
  color: inherit; /* Allow color transition */
}

.layout-sider.ant-layout-sider-collapsed .logout-btn .nav-text {
  opacity: 0;
  max-width: 0;
  margin-left: 0 !important;
}

.logout-btn:hover {
  background: rgba(239, 68, 68, 0.15) !important; /* Red-500 with low opacity */
  color: #ef4444 !important;
  border-color: rgba(239, 68, 68, 0.2);
  /* transform removed */
}

/* Header & Content Layout Logic */
.layout-header {
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(8px);
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05); /* Minimalist shadow */
  position: sticky;
  top: 0;
  z-index: 99;
  margin-left: 260px;
  height: 64px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.layout-sider.ant-layout-sider-collapsed + .ant-layout .layout-header {
  margin-left: 80px;
}

.trigger {
  font-size: 20px;
  cursor: pointer;
  padding: 8px;
  border-radius: 8px;
  transition: all 0.3s;
  color: #64748b;
}

.trigger:hover {
  background-color: #f1f5f9;
  color: #0f172a;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-name {
  font-weight: 600;
  color: #334155;
  font-size: 15px;
}

.layout-content {
  margin: 24px 24px 24px 284px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.layout-sider.ant-layout-sider-collapsed + .ant-layout .layout-content {
  margin-left: 104px;
}

/* Mobile Styles */
@media (max-width: 768px) {
  .layout-header {
    margin-left: 0 !important;
  }

  .layout-content {
    margin: 16px !important;
  }

  .mobile-sider {
    transform: translateX(-100%);
    box-shadow: 10px 0 30px rgba(0, 0, 0, 0.3); /* Stronger shadow on mobile */
  }

  .mobile-sider.show {
    transform: translateX(0);
  }

  .mobile-overlay {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(15, 23, 42, 0.6); /* Blurry dark overlay */
    backdrop-filter: blur(4px);
    z-index: 99;
    animation: fadeIn 0.3s ease;
  }
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

/* Vue Transitions */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateX(10px); /* X-axis slide */
}
</style>
