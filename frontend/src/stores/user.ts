import { defineStore } from 'pinia'
import { authApi } from '@/api'
import type { User, LoginRequest } from '@/types'
import { message } from 'ant-design-vue'

interface UserState {
  user: User | null
  token: string | null
  isAuthenticated: boolean
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    user: null,
    token: localStorage.getItem('token'),
    isAuthenticated: !!localStorage.getItem('token'),
  }),

  actions: {
    // 登录
    async login(loginData: LoginRequest) {
      try {
        const res = await authApi.login(loginData)
        const { token, userId, username, nickname, email, role } = res.data

        this.token = token
        this.isAuthenticated = true
        this.user = {
          id: userId,
          username,
          nickname,
          email,
          role,
          status: 'ACTIVE',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        }

        localStorage.setItem('token', token)
        localStorage.setItem('userId', userId.toString())
        localStorage.setItem('userRole', role)

        message.success('登录成功')
        return true
      } catch (error) {
        console.error('Login error:', error)
        return false
      }
    },

    // 注册
    async register(userData: Partial<User>) {
      try {
        await authApi.register(userData)
        message.success('注册成功，请登录')
        return true
      } catch (error) {
        console.error('Register error:', error)
        return false
      }
    },

    // 获取当前用户信息
    async fetchCurrentUser() {
      try {
        const res = await authApi.getCurrentUser()
        this.user = res.data
        // 保存用户角色到localStorage
        if (res.data.role) {
          localStorage.setItem('userRole', res.data.role)
        }
        return true
      } catch (error) {
        console.error('Fetch user error:', error)
        this.logout()
        return false
      }
    },

    // 更新用户信息
    async updateProfile(userData: Partial<User>) {
      try {
        await authApi.updateProfile(userData)
        if (this.user) {
          this.user = { ...this.user, ...userData }
        }
        message.success('更新成功')
        return true
      } catch (error) {
        console.error('Update profile error:', error)
        return false
      }
    },

    // 修改密码
    async changePassword(oldPassword: string, newPassword: string) {
      try {
        await authApi.changePassword(oldPassword, newPassword)
        message.success('密码修改成功，请重新登录')
        this.logout()
        return true
      } catch (error) {
        console.error('Change password error:', error)
        return false
      }
    },

    // 登出
    logout() {
      this.user = null
      this.token = null
      this.isAuthenticated = false
      localStorage.removeItem('token')
      localStorage.removeItem('userId')
      message.info('已退出登录')
    },

    // 清理所有状态（用于退出登录时）
    clearAll() {
      this.user = null
      this.token = null
      this.isAuthenticated = false
      localStorage.removeItem('token')
      localStorage.removeItem('userId')
      localStorage.removeItem('userRole')
    },
  },
})
