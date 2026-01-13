import { describe, it, expect, beforeEach, vi, afterEach, Mock } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api'

// Mock API模块
vi.mock('@/api', () => ({
  authApi: {
    login: vi.fn(),
    register: vi.fn(),
    sendVerificationCode: vi.fn(),
    registerWithCode: vi.fn(),
    getCurrentUser: vi.fn(),
    updateProfile: vi.fn(),
    changePassword: vi.fn(),
  },
}))

// Mock ant-design-vue message
vi.mock('ant-design-vue', () => ({
  message: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  },
}))

// Mock localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: vi.fn((key: string) => store[key] || null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value
    }),
    removeItem: vi.fn((key: string) => {
      delete store[key]
    }),
    clear: vi.fn(() => {
      store = {}
    }),
  }
})()
Object.defineProperty(window, 'localStorage', { value: localStorageMock })

// Mock window.location
const mockLocation = { href: '' }
Object.defineProperty(window, 'location', {
  value: mockLocation,
  writable: true,
})

/**
 * User Store 单元测试
 * P3 优先级 - 前端状态管理
 */
describe('User Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorageMock.clear()
    mockLocation.href = ''
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('初始状态', () => {
    it('应该有正确的初始状态', () => {
      const store = useUserStore()

      expect(store.user).toBeNull()
      expect(store.token).toBeNull()
      expect(store.isAuthenticated).toBe(false)
    })

    it('如果localStorage有token应该设置为已认证', () => {
      localStorageMock.getItem.mockReturnValueOnce('stored-token')
      
      // 需要重新创建pinia和store来读取localStorage
      setActivePinia(createPinia())
      const store = useUserStore()

      expect(store.token).toBe('stored-token')
      expect(store.isAuthenticated).toBe(true)
    })
  })

  describe('login action', () => {
    it('登录成功应该设置用户状态和token', async () => {
      const store = useUserStore()
      const mockResponse = {
        data: {
          token: 'test-jwt-token',
          userId: 1,
          username: 'testuser',
          nickname: '测试用户',
          email: 'test@example.com',
          role: 'USER',
        },
      }

      ;(authApi.login as Mock).mockResolvedValueOnce(mockResponse)

      const result = await store.login({
        username: 'testuser',
        password: 'password123',
      })

      expect(result).toBe(true)
      expect(store.token).toBe('test-jwt-token')
      expect(store.isAuthenticated).toBe(true)
      expect(store.user).toMatchObject({
        id: 1,
        username: 'testuser',
        nickname: '测试用户',
        email: 'test@example.com',
        role: 'USER',
      })
      expect(localStorageMock.setItem).toHaveBeenCalledWith('token', 'test-jwt-token')
      expect(localStorageMock.setItem).toHaveBeenCalledWith('userId', '1')
      expect(localStorageMock.setItem).toHaveBeenCalledWith('userRole', 'USER')
    })

    it('登录失败应该返回false', async () => {
      const store = useUserStore()

      ;(authApi.login as Mock).mockRejectedValueOnce(new Error('登录失败'))

      const result = await store.login({
        username: 'wronguser',
        password: 'wrongpassword',
      })

      expect(result).toBe(false)
      expect(store.token).toBeNull()
      expect(store.isAuthenticated).toBe(false)
      expect(store.user).toBeNull()
    })
  })

  describe('register action', () => {
    it('注册成功应该返回true', async () => {
      const store = useUserStore()

      ;(authApi.register as Mock).mockResolvedValueOnce({ data: {} })

      const registerData = {
        username: 'newuser',
        password: 'password123',
        email: 'new@example.com',
      } as any

      const result = await store.register(registerData)

      expect(result).toBe(true)
      expect(authApi.register).toHaveBeenCalledWith(registerData)
    })

    it('注册失败应该返回false', async () => {
      const store = useUserStore()

      ;(authApi.register as Mock).mockRejectedValueOnce(new Error('注册失败'))

      const result = await store.register({
        username: 'existinguser',
        password: 'password123',
      } as any)

      expect(result).toBe(false)
    })
  })

  describe('sendVerificationCode action', () => {
    it('发送验证码成功应该返回true', async () => {
      const store = useUserStore()

      ;(authApi.sendVerificationCode as Mock).mockResolvedValueOnce({ data: {} })

      const result = await store.sendVerificationCode('test@example.com', 'REGISTER')

      expect(result).toBe(true)
      expect(authApi.sendVerificationCode).toHaveBeenCalledWith({
        email: 'test@example.com',
        type: 'REGISTER',
      })
    })

    it('发送验证码失败应该返回false', async () => {
      const store = useUserStore()

      ;(authApi.sendVerificationCode as Mock).mockRejectedValueOnce({
        response: { data: { message: '发送太频繁' } },
      })

      const result = await store.sendVerificationCode('test@example.com')

      expect(result).toBe(false)
    })
  })

  describe('registerWithCode action', () => {
    it('带验证码注册成功应该返回true', async () => {
      const store = useUserStore()

      ;(authApi.registerWithCode as Mock).mockResolvedValueOnce({ data: {} })

      const result = await store.registerWithCode({
        username: 'codeuser',
        password: 'password123',
        email: 'code@example.com',
        nickname: '验证码用户',
        verificationCode: '123456',
      })

      expect(result).toBe(true)
      expect(authApi.registerWithCode).toHaveBeenCalledWith({
        username: 'codeuser',
        password: 'password123',
        email: 'code@example.com',
        nickname: '验证码用户',
        verificationCode: '123456',
      })
    })

    it('验证码无效应该返回false', async () => {
      const store = useUserStore()

      ;(authApi.registerWithCode as Mock).mockRejectedValueOnce({
        response: { data: { message: '验证码无效' } },
      })

      const result = await store.registerWithCode({
        username: 'codeuser',
        password: 'password123',
        email: 'code@example.com',
        verificationCode: 'wrong',
      })

      expect(result).toBe(false)
    })
  })

  describe('fetchCurrentUser action', () => {
    it('获取用户信息成功应该更新用户状态', async () => {
      const store = useUserStore()
      const mockUser = {
        id: 1,
        username: 'testuser',
        nickname: '测试用户',
        email: 'test@example.com',
        role: 'ADMIN',
        status: 'ACTIVE',
      }

      ;(authApi.getCurrentUser as Mock).mockResolvedValueOnce({ data: mockUser })

      const result = await store.fetchCurrentUser()

      expect(result).toBe(true)
      expect(store.user).toEqual(mockUser)
      expect(localStorageMock.setItem).toHaveBeenCalledWith('userRole', 'ADMIN')
    })

    it('获取用户信息失败应该登出', async () => {
      const store = useUserStore()
      store.token = 'some-token'
      store.isAuthenticated = true

      ;(authApi.getCurrentUser as Mock).mockRejectedValueOnce(new Error('未授权'))

      const result = await store.fetchCurrentUser()

      expect(result).toBe(false)
      expect(store.token).toBeNull()
      expect(store.isAuthenticated).toBe(false)
    })
  })

  describe('updateProfile action', () => {
    it('更新用户信息成功应该更新本地状态', async () => {
      const store = useUserStore()
      store.user = {
        id: 1,
        username: 'testuser',
        nickname: '旧昵称',
        email: 'test@example.com',
        role: 'USER',
        status: 'ACTIVE',
        createdAt: '2024-01-01',
        updatedAt: '2024-01-01',
      }

      ;(authApi.updateProfile as Mock).mockResolvedValueOnce({ data: {} })

      const result = await store.updateProfile({ nickname: '新昵称' })

      expect(result).toBe(true)
      expect(store.user?.nickname).toBe('新昵称')
    })

    it('更新用户信息失败应该返回false', async () => {
      const store = useUserStore()
      store.user = {
        id: 1,
        username: 'testuser',
        nickname: '旧昵称',
        email: 'test@example.com',
        role: 'USER',
        status: 'ACTIVE',
        createdAt: '2024-01-01',
        updatedAt: '2024-01-01',
      }

      ;(authApi.updateProfile as Mock).mockRejectedValueOnce(new Error('更新失败'))

      const result = await store.updateProfile({ nickname: '新昵称' })

      expect(result).toBe(false)
    })
  })

  describe('changePassword action', () => {
    it('修改密码成功应该返回true', async () => {
      vi.useFakeTimers()
      const store = useUserStore()

      ;(authApi.changePassword as Mock).mockResolvedValueOnce({ data: {} })

      const result = await store.changePassword('oldpass', 'newpass', '123456')

      expect(result).toBe(true)
      expect(authApi.changePassword).toHaveBeenCalledWith('oldpass', 'newpass', '123456')

      // 验证延迟后会登出
      vi.advanceTimersByTime(1500)
      expect(store.token).toBeNull()

      vi.useRealTimers()
    })

    it('修改密码失败应该返回false', async () => {
      const store = useUserStore()

      ;(authApi.changePassword as Mock).mockRejectedValueOnce(new Error('原密码错误'))

      const result = await store.changePassword('wrongpass', 'newpass', '123456')

      expect(result).toBe(false)
    })
  })

  describe('logout action', () => {
    it('登出应该清除所有状态并跳转', () => {
      const store = useUserStore()
      store.user = { id: 1 } as any
      store.token = 'test-token'
      store.isAuthenticated = true

      store.logout()

      expect(store.user).toBeNull()
      expect(store.token).toBeNull()
      expect(store.isAuthenticated).toBe(false)
      expect(localStorageMock.removeItem).toHaveBeenCalledWith('token')
      expect(localStorageMock.removeItem).toHaveBeenCalledWith('userId')
      expect(mockLocation.href).toBe('/student4/login')
    })
  })

  describe('clearAll action', () => {
    it('应该清除所有状态', () => {
      const store = useUserStore()
      store.user = { id: 1 } as any
      store.token = 'test-token'
      store.isAuthenticated = true

      store.clearAll()

      expect(store.user).toBeNull()
      expect(store.token).toBeNull()
      expect(store.isAuthenticated).toBe(false)
      expect(localStorageMock.removeItem).toHaveBeenCalledWith('token')
      expect(localStorageMock.removeItem).toHaveBeenCalledWith('userId')
      expect(localStorageMock.removeItem).toHaveBeenCalledWith('userRole')
    })
  })
})
