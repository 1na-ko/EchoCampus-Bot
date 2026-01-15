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

    // 发送验证码
    async sendVerificationCode(email: string, type = 'REGISTER') {
      try {
        await authApi.sendVerificationCode({ email, type })
        message.success('验证码已发送，请查收邮件')
        return true
      } catch (error: any) {
        console.error('Send verification code error:', error)
        const errorMsg = error?.response?.data?.message || '验证码发送失败'
        message.error(errorMsg)
        return false
      }
    },

    // 注册（带验证码）
    async registerWithCode(data: {
      username: string
      password: string
      email: string
      nickname?: string
      verificationCode: string
    }) {
      try {
        await authApi.registerWithCode(data)
        message.success('注册成功，请登录')
        return true
      } catch (error: any) {
        console.error('Register with code error:', error)
        const errorMsg = error?.response?.data?.message || '注册失败'
        message.error(errorMsg)
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
    async updateProfile(userData: { nickname?: string; email?: string; emailVerificationCode?: string }) {
      try {
        await authApi.updateProfile(userData)
        if (this.user) {
          // 只更新昵称和邮箱
          if (userData.nickname) {
            this.user.nickname = userData.nickname
          }
          if (userData.email) {
            this.user.email = userData.email
          }
        }
        return true
      } catch (error: any) {
        console.error('Update profile error:', error)
        const errorMsg = error?.response?.data?.message || '更新失败'
        message.error(errorMsg)
        return false
      }
    },

    // 修改密码
    async changePassword(oldPassword: string, newPassword: string, verificationCode: string) {
      try {
        await authApi.changePassword(oldPassword, newPassword, verificationCode)
        message.success('密码修改成功，请重新登录')
        setTimeout(() => {
          this.logout()
        }, 1500)
        return true
      } catch (error: any) {
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
      window.location.href = '/student4/login'
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
