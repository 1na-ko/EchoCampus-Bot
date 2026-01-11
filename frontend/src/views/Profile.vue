<template>
  <div class="profile-view">
    <!-- Immersive Background -->
    <div class="immersive-bg">
      <div class="bg-shape shape-1"></div>
      <div class="bg-shape shape-2"></div>
    </div>

    <div class="content-wrapper">
      <!-- Header -->
      <div class="glass-header">
        <h1 class="page-title">个人中心</h1>
      </div>

      <div class="profile-content">
        <div class="profile-grid">
          <!-- Left Column: User Info -->
          <div class="glass-card info-card">
            <div class="card-header">
              <div class="header-main">
                 <div class="header-icon-wrapper">
                    <UserOutlined />
                 </div>
                 <div>
                    <h2 class="card-title">基本信息</h2>
                    <p class="card-subtitle">管理您的个人资料和账号状态</p>
                 </div>
              </div>
              
              <div class="card-actions">
                <transition name="fade" mode="out-in">
                  <a-button 
                    v-if="!isEditing" 
                    type="text" 
                    class="action-btn-ghost"
                    @click="isEditing = true"
                  >
                    <EditOutlined /> 编辑
                  </a-button>
                  <div v-else class="action-group">
                    <a-button type="text" class="cancel-btn" @click="cancelEdit">取消</a-button>
                    <a-button type="primary" class="save-btn" :loading="saving" @click="handleSave">
                      保存
                    </a-button>
                  </div>
                </transition>
              </div>
            </div>

            <div class="card-body">
               <div class="avatar-section">
                  <div class="avatar-wrapper">
                     <a-avatar :size="80" class="profile-avatar">
                        {{ formData.nickname?.[0]?.toUpperCase() || formData.username?.[0]?.toUpperCase() || 'U' }}
                     </a-avatar>
                     <div class="role-badge">
                        {{ formData.role }}
                     </div>
                  </div>
                  <div class="user-identity">
                     <h3>{{ formData.nickname || formData.username }}</h3>
                     <span class="user-handle">@{{ formData.username }}</span>
                  </div>
               </div>

               <a-form :model="formData" layout="vertical" class="profile-form">
                  <div class="form-grid">
                     <a-form-item label="昵称" class="form-item">
                        <a-input
                           v-model:value="formData.nickname"
                           :disabled="!isEditing"
                           placeholder="怎么称呼您？"
                           class="glass-input"
                           :bordered="false"
                        />
                     </a-form-item>

                     <a-form-item label="电子邮箱" class="form-item">
                        <a-input
                           v-model:value="formData.email"
                           :disabled="!isEditing"
                           placeholder="your@email.com"
                           class="glass-input"
                           :bordered="false"
                        />
                     </a-form-item>
                     
                     <a-form-item label="注册时间" class="form-item">
                        <div class="readonly-field">
                           {{ dayjs(formData.createdAt).format('YYYY年MM月DD日') }}
                        </div>
                     </a-form-item>

                     <a-form-item label="账号状态" class="form-item">
                        <div class="status-indicator" :class="formData.status === 'ACTIVE' ? 'active' : 'inactive'">
                           <span class="status-dot"></span>
                           {{ formData.status === 'ACTIVE' ? '账号正常' : formData.status }}
                        </div>
                     </a-form-item>
                  </div>
               </a-form>
            </div>
          </div>

          <!-- Right Column: Security -->
          <div class="glass-card security-card">
            <div class="card-header">
               <div class="header-main">
                 <div class="header-icon-wrapper security">
                    <LockOutlined />
                 </div>
                 <div>
                    <h2 class="card-title">安全设置</h2>
                    <p class="card-subtitle">更新您的登录密码</p>
                 </div>
               </div>
            </div>
            
            <div class="card-body">
               <div class="security-intro">
                  为了保障您的账户安全，建议您定期更换密码，并使用且包含字母、数字的强密码组合。
               </div>

               <a-form :model="passwordForm" :rules="passwordRules" layout="vertical" class="profile-form">
                  <a-form-item label="当前密码" name="oldPassword" class="form-item password-form-item">
                  <a-input-password
                     v-model:value="passwordForm.oldPassword"
                     placeholder="输入当前使用的密码"
                     class="glass-input"
                     :bordered="false"
                  />
                  </a-form-item>

                  <a-form-item label="新密码" name="newPassword" class="form-item password-form-item">
                  <a-input-password
                     v-model:value="passwordForm.newPassword"
                     placeholder="设置新密码（至少6位）"
                     class="glass-input"
                     :bordered="false"
                  />
                  </a-form-item>

                  <a-form-item label="确认新密码" name="confirmPassword" class="form-item password-form-item">
                  <a-input-password
                     v-model:value="passwordForm.confirmPassword"
                     placeholder="再次输入以确认"
                     class="glass-input"
                     :bordered="false"
                  />
                  </a-form-item>

                  <a-form-item label="邮箱验证码" name="verificationCode" class="form-item password-form-item">
                    <div style="display: flex; gap: 8px;">
                      <a-input
                         v-model:value="passwordForm.verificationCode"
                         placeholder="请输入6位验证码"
                         class="glass-input"
                         :bordered="false"
                         style="flex: 1;"
                         :maxlength="6"
                      />
                      <a-button
                        class="glass-input"
                        :disabled="countdown > 0"
                        :loading="sendingCode"
                        @click="handleSendCode"
                        style="width: 120px; font-size: 13px; color: var(--primary-color); font-weight: 500;"
                      >
                        {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
                      </a-button>
                    </div>
                  </a-form-item>

                  <div class="form-actions-bottom">
                  <a-button
                     type="primary"
                     block
                     size="large"
                     class="submit-btn"
                     :loading="changingPassword"
                     @click="handleChangePassword"
                  >
                     更新密码
                  </a-button>
                  </div>
               </a-form>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '@/stores/user'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  EditOutlined,
  UserOutlined,
  LockOutlined,
} from '@ant-design/icons-vue'

const userStore = useUserStore()

const isEditing = ref(false)
const saving = ref(false)
const changingPassword = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
let countdownTimer: number | null = null

const formData = reactive({
  username: '',
  nickname: '',
  email: '',
  role: '',
  status: '',
  createdAt: '',
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
  verificationCode: '',
})

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入当前密码' }],
  newPassword: [
    { required: true, message: '请输入新密码' },
    { min: 6, message: '密码至少6个字符' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码' },
    {
      validator: (_: any, value: string) => {
        if (value !== passwordForm.newPassword) {
          return Promise.reject('两次输入的密码不一致')
        }
        return Promise.resolve()
      },
    },
  ],
  verificationCode: [
    { required: true, message: '请输入验证码' },
    { len: 6, message: '验证码必须为6位数字' },
  ],
}

const loadUserData = () => {
  if (userStore.user) {
    Object.assign(formData, {
      username: userStore.user.username,
      nickname: userStore.user.nickname,
      email: userStore.user.email,
      role: userStore.user.role,
      status: userStore.user.status,
      createdAt: userStore.user.createdAt,
    })
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    const success = await userStore.updateProfile({
      nickname: formData.nickname,
      email: formData.email,
    })
    if (success) {
      isEditing.value = false
      message.success('个人信息更新成功')
    }
  } finally {
    saving.value = false
  }
}

const cancelEdit = () => {
  isEditing.value = false
  loadUserData()
}

const handleSendCode = async () => {
  if (!formData.email) {
    message.warning('请先绑定邮箱')
    return
  }
  
  sendingCode.value = true
  try {
    const success = await userStore.sendVerificationCode(formData.email, 'CHANGE_PASSWORD')
    if (success) {
      startCountdown()
    }
  } finally {
    sendingCode.value = false
  }
}

const startCountdown = () => {
  countdown.value = 60
  countdownTimer = window.setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      stopCountdown()
    }
  }, 1000)
}

const stopCountdown = () => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
  countdown.value = 0
}

const handleChangePassword = async () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword || !passwordForm.verificationCode) {
    message.error('请填写完整信息')
    return
  }

  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    message.error('两次输入的密码不一致')
    return
  }

  changingPassword.value = true
  try {
    await userStore.changePassword(
      passwordForm.oldPassword,
      passwordForm.newPassword,
      passwordForm.verificationCode
    )
    Object.assign(passwordForm, {
      oldPassword: '',
      newPassword: '',
      confirmPassword: '',
      verificationCode: '',
    })
    message.success('密码修改成功')
    stopCountdown()
  } finally {
    changingPassword.value = false
  }
}

onUnmounted(() => {
  stopCountdown()
})

onMounted(async () => {
  if (!userStore.user) {
    await userStore.fetchCurrentUser()
  }
  loadUserData()
})
</script>

<style scoped>
/* Page Layout */
.profile-view {
  min-height: 100vh;
  position: relative;
  background-color: var(--bg-light);
  overflow-x: hidden;
}

.immersive-bg {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.bg-shape {
  position: absolute;
  filter: blur(100px);
  opacity: 0.6;
  z-index: 0;
}

.shape-1 {
  top: -10%;
  right: -10%;
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.2) 0%, rgba(168, 85, 247, 0.05) 70%);
  border-radius: 50%;
  animation: float 20s infinite ease-in-out;
}

.shape-2 {
  bottom: -10%;
  left: -10%;
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.15) 0%, rgba(14, 165, 233, 0.05) 70%);
  border-radius: 50%;
  animation: float 25s infinite ease-in-out reverse;
}

@keyframes float {
  0% { transform: translate(0, 0) rotate(0deg); }
  33% { transform: translate(30px, -50px) rotate(10deg); }
  66% { transform: translate(-20px, 20px) rotate(-5deg); }
  100% { transform: translate(0, 0) rotate(0deg); }
}

.content-wrapper {
  position: relative;
  z-index: 1;
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 32px;
}

/* Header */
.glass-header {
  margin-bottom: 32px;
  padding: 16px 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-title {
  font-size: 28px;
  font-weight: 800;
  background: linear-gradient(135deg, var(--text-primary) 0%, var(--text-secondary) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin: 0;
  letter-spacing: -0.5px;
}

/* Grid Layout */
.profile-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 32px;
}

/* Glass Card */
.glass-card {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 24px;
  box-shadow: 
    0 4px 6px -1px rgba(0, 0, 0, 0.02),
    0 2px 4px -1px rgba(0, 0, 0, 0.02),
    inset 0 0 0 1px rgba(255, 255, 255, 0.5);
  overflow: hidden;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.glass-card:hover {
  box-shadow: 
    0 10px 30px -5px rgba(0, 0, 0, 0.05),
    0 5px 15px -3px rgba(0, 0, 0, 0.03),
    inset 0 0 0 1px rgba(255, 255, 255, 0.6);
}

.card-header {
  padding: 24px 32px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.03);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.header-main {
  display: flex;
  gap: 16px;
  align-items: center;
}

.header-icon-wrapper {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.1) 0%, rgba(99, 102, 241, 0.05) 100%);
  color: var(--primary-color);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.header-icon-wrapper.security {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.1) 0%, rgba(16, 185, 129, 0.05) 100%);
  color: #10b981;
}

.card-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  line-height: 1.2;
}

.card-subtitle {
  font-size: 13px;
  color: var(--text-tertiary);
  margin-top: 4px;
  font-weight: 500;
}

.card-body {
  padding: 32px;
}

/* Avatar Section */
.avatar-section {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 32px;
  padding-bottom: 24px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.03);
}

.avatar-wrapper {
  position: relative;
}

.profile-avatar {
  background: linear-gradient(135deg, var(--primary-color) 0%, #818cf8 100%);
  font-size: 32px;
  font-weight: 600;
  border: 4px solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.2);
}

.role-badge {
  position: absolute;
  bottom: -4px;
  right: -8px;
  background: #333;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 10px;
  border: 2px solid #fff;
}

.user-identity h3 {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

.user-handle {
  font-size: 14px;
  color: var(--text-tertiary);
}

/* Form Styling */
.profile-form .form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

.profile-form .form-item {
  margin-bottom: 0;
}

:deep(.profile-form .ant-form-item-label > label) {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

:deep(.glass-input) {
  background: rgba(243, 244, 246, 0.6) !important;
  border: 1px solid transparent !important;
  border-radius: 12px !important;
  padding: 8px 16px !important;
  font-size: 14px !important;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1) !important;
  box-shadow: none !important;
}

:deep(.glass-input:hover) {
  background: rgba(243, 244, 246, 0.8) !important;
}

:deep(.glass-input:focus) {
  background: #fff !important;
  border-color: var(--primary-color) !important;
  box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.1) !important;
}

:deep(.glass-input.ant-input-disabled) {
  background: transparent !important;
  color: var(--text-tertiary) !important;
  cursor: default;
  padding-left: 0 !important;
}

.readonly-field {
  padding: 8px 0;
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 500;
}

.status-indicator {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: rgba(0, 0, 0, 0.03);
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
}

.status-indicator.active {
  background: rgba(16, 185, 129, 0.1);
  color: #059669;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #94a3b8;
}

.status-indicator.active .status-dot {
  background-color: #10b981;
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.2);
}

/* Security Section specific */
.security-intro {
  margin-bottom: 16px;
  font-size: 14px;
  color: var(--text-tertiary);
  line-height: 1.6;
  background: rgba(249, 250, 251, 0.5);
  padding: 12px 16px;
  border-radius: 12px;
}

.password-form-item {
  margin-bottom: 24px;
}

.form-actions-bottom {
  margin-top: 32px;
}

.submit-btn {
  height: 48px;
  border-radius: 14px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  border: none;
  background: linear-gradient(135deg, var(--primary-color) 0%, #818cf8 100%);
  transition: all 0.3s;
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.4);
}

/* Buttons */
.action-btn-ghost {
  color: var(--text-secondary);
  font-weight: 500;
  transition: all 0.2s;
}
.action-btn-ghost:hover {
  color: var(--primary-color);
  background: rgba(99, 102, 241, 0.05);
}

.action-group {
  display: flex;
  gap: 12px;
}

.save-btn {
  border-radius: 8px;
  font-weight: 600;
}

/* Responsive */
@media (max-width: 900px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .content-wrapper {
    padding: 16px;
  }
  .card-body {
    padding: 24px;
  }
  .form-grid {
    grid-template-columns: 1fr !important;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
