<template>
  <div id="app">
    <el-container class="app-container">
      <!-- 侧边导航 -->
      <el-aside width="200px" class="sidebar">
        <div class="logo">
          <h2>EchoCampus</h2>
          <p>智能校园助手</p>
        </div>
        
        <el-menu
          :default-active="currentRoute"
          router
          class="menu"
        >
          <el-menu-item index="/chat">
            <el-icon><ChatDotRound /></el-icon>
            <span>智能问答</span>
          </el-menu-item>
          <el-menu-item index="/knowledge">
            <el-icon><Document /></el-icon>
            <span>知识库管理</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <!-- 主内容区 -->
      <el-container class="main-container">
        <el-main class="content">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const currentRoute = computed(() => route.path)
</script>

<style scoped>
.app-container {
  height: 100vh;
}

.sidebar {
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.logo {
  padding: 24px 16px;
  text-align: center;
  border-bottom: 1px solid #f0f0f0;
}

.logo h2 {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.logo p {
  font-size: 12px;
  color: #909399;
}

.menu {
  flex: 1;
  border: none;
  padding-top: 16px;
}

.main-container {
  background: #f5f7fa;
}

.content {
  padding: 0;
  height: 100%;
}
</style>
