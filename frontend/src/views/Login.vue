<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <h1 class="login-title">EchoCampus Bot</h1>
        <p class="login-subtitle">智能IT知识问答助手</p>
      </div>

      <a-tabs v-model:activeKey="activeTab" centered>
        <a-tab-pane key="login" tab="登录">
          <a-form
            :model="loginForm"
            :rules="loginRules"
            @finish="handleLogin"
            layout="vertical"
            class="login-form"
          >
            <a-form-item name="username" label="用户名">
              <a-input
                v-model:value="loginForm.username"
                size="large"
                placeholder="请输入用户名"
                :prefix="h(UserOutlined)"
              />
            </a-form-item>

            <a-form-item name="password" label="密码">
              <a-input-password
                v-model:value="loginForm.password"
                size="large"
                placeholder="请输入密码"
                :prefix="h(LockOutlined)"
              />
            </a-form-item>

            <a-form-item>
              <a-button
                type="primary"
                html-type="submit"
                size="large"
                block
                :loading="loading"
              >
                登录
              </a-button>
            </a-form-item>
          </a-form>
        </a-tab-pane>

        <a-tab-pane key="register" tab="注册">
          <a-form
            :model="registerForm"
            :rules="registerRules"
            @finish="handleRegister"
            layout="vertical"
            class="login-form"
          >
            <a-form-item name="username" label="用户名">
              <a-input
                v-model:value="registerForm.username"
                size="large"
                placeholder="3-50个字符"
                :prefix="h(UserOutlined)"
              />
            </a-form-item>

            <a-form-item name="email" label="邮箱">
              <a-input
                v-model:value="registerForm.email"
                size="large"
                placeholder="请输入邮箱"
                :prefix="h(MailOutlined)"
              />
            </a-form-item>

            <a-form-item name="password" label="密码">
              <a-input-password
                v-model:value="registerForm.password"
                size="large"
                placeholder="至少6个字符"
                :prefix="h(LockOutlined)"
              />
            </a-form-item>

            <a-form-item name="confirmPassword" label="确认密码">
              <a-input-password
                v-model:value="registerForm.confirmPassword"
                size="large"
                placeholder="请再次输入密码"
                :prefix="h(LockOutlined)"
              />
            </a-form-item>

            <a-form-item>
              <a-button
                type="primary"
                html-type="submit"
                size="large"
                block
                :loading="loading"
              >
                注册
              </a-button>
            </a-form-item>
          </a-form>
        </a-tab-pane>
      </a-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { h } from 'vue'
import { useRouter } from 'vue-router'
import { UserOutlined, LockOutlined, MailOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import type { LoginRequest, User } from '@/types'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('login')
const loading = ref(false)

const loginForm = reactive<LoginRequest>({
  username: '',
  password: '',
})

const registerForm = reactive<Partial<User> & { confirmPassword?: string }>({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  role: 'USER',
  status: 'ACTIVE',
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名' }],
  password: [{ required: true, message: '请输入密码' }],
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名' },
    { min: 3, max: 50, message: '用户名长度3-50字符' },
  ],
  email: [
    { required: true, message: '请输入邮箱' },
    { type: 'email', message: '请输入有效的邮箱地址' },
  ],
  password: [
    { required: true, message: '请输入密码' },
    { min: 6, message: '密码至少6个字符' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码' },
    {
      validator: (_: any, value: string) => {
        if (value !== registerForm.password) {
          return Promise.reject('两次输入的密码不一致')
        }
        return Promise.resolve()
      },
    },
  ],
}

const handleLogin = async () => {
  loading.value = true
  try {
    const success = await userStore.login(loginForm)
    if (success) {
      router.push('/chat')
    }
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  loading.value = true
  try {
    const { confirmPassword, ...userData } = registerForm
    userData.nickname = userData.username
    const success = await userStore.register(userData)
    if (success) {
      activeTab.value = 'login'
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-color);
  background-image: 
    radial-gradient(at 0% 0%, rgba(99, 102, 241, 0.15) 0px, transparent 50%),
    radial-gradient(at 100% 100%, rgba(236, 72, 153, 0.15) 0px, transparent 50%);
  padding: 20px;
}

.login-box {
  width: 100%;
  max-width: 440px;
  background: var(--surface-color-transparent);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid var(--surface-color);
  border-radius: var(--radius-xl);
  padding: 48px 40px;
  box-shadow: var(--shadow-xl);
  animation: slideUp 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.login-header {
  text-align: center;
  margin-bottom: 40px;
}

.login-title {
  font-size: 32px;
  font-weight: 800;
  margin: 0 0 12px;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -0.5px;
}

.login-subtitle {
  font-size: 15px;
  color: var(--text-secondary);
  margin: 0;
  font-weight: 500;
}

.login-form {
  margin-top: 24px;
}

/* Ant Design overrides within scoped style for specific adjustments */
:deep(.ant-tabs-nav) {
  margin-bottom: 24px;
}

:deep(.ant-tabs-tab) {
  font-size: 16px;
  padding: 12px 0;
  transition: color var(--transition-fast);
}

:deep(.ant-input-affix-wrapper) {
  padding: 10px 11px;
  border-radius: var(--radius-md);
  border-color: var(--border-color);
  box-shadow: none;
  transition: all var(--transition-normal);
}

:deep(.ant-input-affix-wrapper:hover) {
  border-color: var(--primary-hover);
}

:deep(.ant-input-affix-wrapper-focused) {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.1);
}

:deep(.ant-btn-primary) {
  height: 48px;
  border-radius: var(--radius-md);
  background: var(--primary-color);
  border: none;
  box-shadow: var(--shadow-md);
  font-weight: 600;
  font-size: 16px;
  transition: all var(--transition-normal);
}

:deep(.ant-btn-primary:hover) {
  background: var(--primary-hover);
  transform: translateY(-1px);
  box-shadow: var(--shadow-lg);
}

@media (max-width: 768px) {
  .login-box {
    padding: 32px 24px;
    max-width: 100%;
    border: none;
    box-shadow: none;
    background: transparent;
    backdrop-filter: none;
  }

  .login-title {
    font-size: 26px;
  }
}
</style>
