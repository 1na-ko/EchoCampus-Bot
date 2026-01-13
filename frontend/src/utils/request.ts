import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { message as antMessage } from 'ant-design-vue'
import type { ApiResponse } from '@/types'

// 创建 axios 实例
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://150.158.97.39:8083/api',
  timeout: 600000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 从 localStorage 获取 token
    const token = localStorage.getItem('token')
    const userId = localStorage.getItem('userId')
    
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    
    if (userId && config.headers) {
      config.headers['X-User-Id'] = userId
    }
    
    // 添加CSRF token支持（如果后端启用了CSRF保护）
    // 从cookie中读取XSRF-TOKEN
    const getCookie = (name: string): string | null => {
      const value = `; ${document.cookie}`
      const parts = value.split(`; ${name}=`)
      if (parts.length === 2) {
        return parts.pop()?.split(';').shift() || null
      }
      return null
    }
    
    const csrfToken = getCookie('XSRF-TOKEN')
    if (csrfToken && config.headers) {
      config.headers['X-XSRF-TOKEN'] = csrfToken
    }
    
    return config
  },
  (error) => {
    console.error('Request error:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data

    // 检查业务状态码
    if (res.code !== 200 && res.code !== 0) {
      antMessage.error(res.message || '请求失败')
      
      // 401 未授权，跳转到登录页
      if (res.code === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('userId')
        window.location.href = '/student4/login'
      }
      
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    
    return response
  },
  (error) => {
    console.error('Response error:', error)
    
    if (error.response) {
      const { status, data } = error.response
      
      switch (status) {
        case 401:
          antMessage.error('未授权，请重新登录')
          localStorage.removeItem('token')
          localStorage.removeItem('userId')
          window.location.href = '/student4/login'
          break
        case 403:
          // 处理权限不足错误
          if (data?.message) {
            antMessage.error(data.message)
          } else {
            antMessage.error('权限不足，无法访问该资源')
          }
          break
        case 404:
          antMessage.error('请求资源不存在')
          break
        case 413:
          // 处理文件过大错误
          antMessage.error('文件过大，请选择小于50MB的文件')
          break
        case 415:
          // 处理不支持的文件类型错误
          antMessage.error('不支持的文件类型')
          break
        case 500:
          // 处理服务器错误，包括文件验证错误
          if (data?.message) {
            // 检查是否是文件验证相关的错误
            if (data.message.includes('文件') || data.message.includes('File')) {
              antMessage.error(data.message)
            } else {
              antMessage.error(data.message || '服务器错误')
            }
          } else {
            antMessage.error('服务器错误')
          }
          break
        case 504:
          antMessage.error(data?.message || '系统繁忙，请稍后再试')
          break
        default:
          antMessage.error(data?.message || '请求失败')
      }
    } else if (error.request) {
      antMessage.error('网络错误，请检查网络连接')
    } else {
      antMessage.error('请求配置错误')
    }
    
    return Promise.reject(error)
  }
)

// 封装请求方法
export const request = {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    return service.get(url, config).then(res => res.data)
  },
  
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    return service.post(url, data, config).then(res => res.data)
  },
  
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    return service.put(url, data, config).then(res => res.data)
  },
  
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    return service.delete(url, config).then(res => res.data)
  },
  
  upload<T = any>(url: string, formData: FormData, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    return service.post(url, formData, {
      ...config,
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    }).then(res => res.data)
  },
}

/**
 * 创建SSE流式请求
 * @param url API URL
 * @param data 请求数据
 * @param onMessage 消息回调
 * @param onError 错误回调
 * @returns AbortController 用于取消请求
 */
export function createSSERequest(
  url: string,
  data: any,
  onMessage: (event: MessageEvent) => void,
  onError?: (error: Error) => void
): AbortController {
  const controller = new AbortController()
  
  const token = localStorage.getItem('token')
  const userId = localStorage.getItem('userId')
  
  // 获取CSRF token
  const getCookie = (name: string): string | null => {
    const value = `; ${document.cookie}`
    const parts = value.split(`; ${name}=`)
    if (parts.length === 2) {
      return parts.pop()?.split(';').shift() || null
    }
    return null
  }
  
  const csrfToken = getCookie('XSRF-TOKEN')
  
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'Accept': 'text/event-stream',
  }
  
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  
  if (userId) {
    headers['X-User-Id'] = userId
  }
  
  if (csrfToken) {
    headers['X-XSRF-TOKEN'] = csrfToken
  }
  
  fetch(`${import.meta.env.VITE_API_BASE_URL || 'http://150.158.97.39:8083/api'}${url}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(data),
    signal: controller.signal,
    credentials: 'include',
  })
    .then(async response => {
      if (!response.ok) {
        let errorMessage = `HTTP error! status: ${response.status}`
        
        try {
          const errorData = await response.json()
          if (errorData.message) {
            errorMessage = errorData.message
          }
        } catch (e) {
          // 如果无法解析JSON，使用默认错误消息
          switch (response.status) {
            case 504:
              errorMessage = '系统繁忙，请稍后再试'
              break
            case 429:
              errorMessage = '请求过于频繁，请稍后再试'
              break
            case 503:
              errorMessage = '服务暂不可用'
              break
          }
        }
        
        throw new Error(errorMessage)
      }
      
      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('Response body is not readable')
      }
      
      const decoder = new TextDecoder()
      let buffer = ''
      
      const processChunk = async () => {
        try {
          while (true) {
            const { done, value } = await reader.read()
            
            if (done) {
              break
            }
            
            buffer += decoder.decode(value, { stream: true })
            
            // 解析SSE格式的数据
            const lines = buffer.split('\n')
            buffer = lines.pop() || '' // 保留不完整的行
            
            for (const line of lines) {
              if (line.startsWith('data:')) {
                const data = line.slice(5).trim()
                if (data) {
                  const event = new MessageEvent('message', { data })
                  onMessage(event)
                }
              }
            }
          }
        } catch (error) {
          const errorObj = error as Error
          if (errorObj.name !== 'AbortError') {
            onError?.(errorObj)
          }
        }
      }
      
      processChunk()
    })
    .catch(error => {
      if (error.name !== 'AbortError') {
        onError?.(error)
      }
    })
  
  return controller
}

export default service
