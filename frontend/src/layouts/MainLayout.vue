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
        <div class="logo-icon">🤖</div>
        <transition name="fade">
          <span v-show="!collapsed" class="logo-text">EchoCampus</span>
        </transition>
      </div>

      <a-menu
        v-model:selectedKeys="selectedKeys"
        theme="dark"
        mode="inline"
        @click="handleMenuClick"
      >
        <a-menu-item key="/chat">
          <MessageOutlined />
          <span>智能问答</span>
        </a-menu-item>
        <a-menu-item key="/knowledge">
          <DatabaseOutlined />
          <span>知识库</span>
        </a-menu-item>
        <a-menu-item key="/profile">
          <UserOutlined />
          <span>个人中心</span>
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
          <span v-show="!collapsed">退出登录</span>
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
import { useBreakpoints } from '@vueuse/core'
import {
  MenuUnfoldOutlined,
  MenuFoldOutlined,
  MessageOutlined,
  DatabaseOutlined,
  UserOutlined,
  LogoutOutlined,
} from '@ant-design/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const collapsed = ref(false)
const showMobileSider = ref(false)
const selectedKeys = ref([route.path])

const breakpoints = useBreakpoints({
  mobile: 768,
  tablet: 1024,
})

const isMobile = breakpoints.smaller('mobile')

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

const handleMenuClick = ({ key }: { key: string }) => {
  router.push(key)
  if (isMobile.value) {
    showMobileSider.value = false
  }
}

const handleLogout = () => {
  userStore.logout()
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
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  overflow: auto;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);
  z-index: 100;
  transition: all 0.2s;
}

.layout-sider :deep(.ant-layout-sider-children) {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(255, 255, 255, 0.1);
  margin: 16px;
  border-radius: 8px;
  transition: all 0.3s;
}

.logo-icon {
  font-size: 28px;
  transition: all 0.3s;
}

.logo-text {
  margin-left: 12px;
  font-size: 20px;
  font-weight: 700;
  color: white;
}

.sider-footer {
  margin-top: auto;
  padding: 16px;
}

.logout-btn {
  color: rgba(255, 255, 255, 0.85);
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logout-btn:hover {
  background: rgba(255, 77, 79, 0.2) !important;
  color: #ff4d4f !important;
}

.layout-header {
  background: white;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  position: sticky;
  top: 0;
  z-index: 99;
  margin-left: 260px;
  transition: all 0.2s;
}

.layout-sider.ant-layout-sider-collapsed + .ant-layout .layout-header {
  margin-left: 80px;
}

.trigger {
  font-size: 20px;
  cursor: pointer;
  transition: color 0.3s;
}

.trigger:hover {
  color: #1890ff;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-name {
  font-weight: 500;
  color: #1a1a1a;
}

.layout-content {
  margin: 24px 24px 24px 284px;
  transition: all 0.2s;
}

.layout-sider.ant-layout-sider-collapsed + .ant-layout .layout-content {
  margin-left: 104px;
}

/* Mobile styles */
@media (max-width: 768px) {
  .layout-header {
    margin-left: 0 !important;
  }

  .layout-content {
    margin: 16px !important;
  }

  .mobile-sider {
    transform: translateX(-100%);
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
    background: rgba(0, 0, 0, 0.45);
    z-index: 99;
  }
}

/* Transitions */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
