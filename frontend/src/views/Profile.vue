<template>
  <div class="profile-container">
    <a-card title="个人信息" class="profile-card">
      <template #extra>
        <a-button v-if="!isEditing" type="primary" @click="isEditing = true">
          <EditOutlined /> 编辑
        </a-button>
        <a-space v-else>
          <a-button @click="cancelEdit">取消</a-button>
          <a-button type="primary" :loading="saving" @click="handleSave">
            保存
          </a-button>
        </a-space>
      </template>

      <a-form :model="formData" layout="vertical">
        <a-row :gutter="24">
          <a-col :xs="24" :md="12">
            <a-form-item label="用户名">
              <a-input v-model:value="formData.username" disabled />
            </a-form-item>
          </a-col>

          <a-col :xs="24" :md="12">
            <a-form-item label="昵称">
              <a-input
                v-model:value="formData.nickname"
                :disabled="!isEditing"
                placeholder="请输入昵称"
              />
            </a-form-item>
          </a-col>

          <a-col :xs="24" :md="12">
            <a-form-item label="邮箱">
              <a-input
                v-model:value="formData.email"
                :disabled="!isEditing"
                type="email"
                placeholder="请输入邮箱"
              />
            </a-form-item>
          </a-col>

          <a-col :xs="24" :md="12">
            <a-form-item label="角色">
              <a-input v-model:value="formData.role" disabled />
            </a-form-item>
          </a-col>

          <a-col :xs="24" :md="12">
            <a-form-item label="状态">
              <a-tag :color="formData.status === 'ACTIVE' ? 'success' : 'default'">
                {{ formData.status === 'ACTIVE' ? '正常' : formData.status }}
              </a-tag>
            </a-form-item>
          </a-col>

          <a-col :xs="24" :md="12">
            <a-form-item label="注册时间">
              <a-input
                :value="dayjs(formData.createdAt).format('YYYY-MM-DD HH:mm:ss')"
                disabled
              />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <a-card title="修改密码" class="profile-card">
      <a-form :model="passwordForm" :rules="passwordRules" layout="vertical">
        <a-row :gutter="24">
          <a-col :xs="24" :md="12">
            <a-form-item label="当前密码" name="oldPassword">
              <a-input-password
                v-model:value="passwordForm.oldPassword"
                placeholder="请输入当前密码"
              />
            </a-form-item>
          </a-col>

          <a-col :xs="24" :md="12"></a-col>

          <a-col :xs="24" :md="12">
            <a-form-item label="新密码" name="newPassword">
              <a-input-password
                v-model:value="passwordForm.newPassword"
                placeholder="至少6个字符"
              />
            </a-form-item>
          </a-col>

          <a-col :xs="24" :md="12">
            <a-form-item label="确认新密码" name="confirmPassword">
              <a-input-password
                v-model:value="passwordForm.confirmPassword"
                placeholder="请再次输入新密码"
              />
            </a-form-item>
          </a-col>

          <a-col :span="24">
            <a-button
              type="primary"
              :loading="changingPassword"
              @click="handleChangePassword"
            >
              修改密码
            </a-button>
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <a-card title="使用统计" class="profile-card">
      <a-row :gutter="24">
        <a-col :xs="12" :md="6">
          <a-statistic title="对话次数" :value="stats.conversationCount">
            <template #prefix>
              <MessageOutlined />
            </template>
          </a-statistic>
        </a-col>

        <a-col :xs="12" :md="6">
          <a-statistic title="消息数量" :value="stats.messageCount">
            <template #prefix>
              <CommentOutlined />
            </template>
          </a-statistic>
        </a-col>

        <a-col :xs="12" :md="6">
          <a-statistic title="上传文档" :value="stats.uploadedDocs">
            <template #prefix>
              <FileOutlined />
            </template>
          </a-statistic>
        </a-col>

        <a-col :xs="12" :md="6">
          <a-statistic title="使用天数" :value="stats.daysActive" suffix="天">
            <template #prefix>
              <CalendarOutlined />
            </template>
          </a-statistic>
        </a-col>
      </a-row>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  EditOutlined,
  MessageOutlined,
  CommentOutlined,
  FileOutlined,
  CalendarOutlined,
} from '@ant-design/icons-vue'

const userStore = useUserStore()
const chatStore = useChatStore()

const isEditing = ref(false)
const saving = ref(false)
const changingPassword = ref(false)

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
}

const stats = reactive({
  conversationCount: 0,
  messageCount: 0,
  uploadedDocs: 0,
  daysActive: 0,
})

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

    // 计算使用天数
    const createdDate = dayjs(userStore.user.createdAt)
    stats.daysActive = dayjs().diff(createdDate, 'day')
  }
}

const loadStats = async () => {
  await chatStore.fetchConversations(1, 100)
  stats.conversationCount = chatStore.conversations.length
  stats.messageCount = chatStore.conversations.reduce(
    (sum, conv) => sum + conv.messageCount,
    0
  )
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
    }
  } finally {
    saving.value = false
  }
}

const cancelEdit = () => {
  isEditing.value = false
  loadUserData()
}

const handleChangePassword = async () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
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
      passwordForm.newPassword
    )
    Object.assign(passwordForm, {
      oldPassword: '',
      newPassword: '',
      confirmPassword: '',
    })
  } finally {
    changingPassword.value = false
  }
}

onMounted(async () => {
  if (!userStore.user) {
    await userStore.fetchCurrentUser()
  }
  loadUserData()
  loadStats()
})
</script>

<style scoped>
.profile-container {
  max-width: 1200px;
  margin: 0 auto;
}

.profile-card {
  margin-bottom: 24px;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.profile-card :deep(.ant-card-head) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 12px 12px 0 0;
}

.profile-card :deep(.ant-card-head-title) {
  color: white;
  font-weight: 600;
}

.profile-card :deep(.ant-btn-primary) {
  background: white;
  color: #667eea;
  border: none;
}

.profile-card :deep(.ant-btn-primary:hover) {
  background: rgba(255, 255, 255, 0.9);
}

@media (max-width: 768px) {
  .profile-container {
    padding: 0;
  }
}
</style>
