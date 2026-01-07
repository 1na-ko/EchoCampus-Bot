import request from '@/utils/request'

/**
 * 发送消息
 */
export function sendMessage(data) {
  return request({
    url: '/v1/chat/message',
    method: 'post',
    data
  })
}

/**
 * 发送消息（流式）
 */
export function sendMessageStream(data, callbacks) {
  const apiUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
  const url = `${apiUrl}/v1/chat/message/stream?message=${encodeURIComponent(data.message)}` + 
              (data.conversationId ? `&conversationId=${data.conversationId}` : '')
  
  // 使用fetch + ReadableStream处理SSE
  fetch(url, {
    method: 'GET',
    headers: {
      'Accept': 'text/event-stream',
      'X-User-Id': '1'
    }
  })
  .then(response => {
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let currentEvent = null
    
    // 读取流
    function read() {
      reader.read().then(({ done, value }) => {
        if (done) {
          return
        }
        
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        
        // 如果最后一行不是空行，说明数据可能不完整
        if (lines[lines.length - 1] !== '') {
          buffer = lines.pop()
        } else {
          buffer = ''
        }
        
        for (const line of lines) {
          if (line.trim() === '') {
            // 空行，表示一个事件结束
            currentEvent = null
            continue
          }
          
          if (line.startsWith('event:')) {
            currentEvent = line.substring(6).trim()
          } else if (line.startsWith('data:')) {
            const eventData = line.substring(5).trim()
            processEvent(currentEvent, eventData)
          }
        }
        
        read()
      })
      .catch(error => {
        if (callbacks.onError) {
          callbacks.onError(error.message)
        }
      })
    }
    
    // 处理事件
    function processEvent(eventType, data) {
      if (eventType === 'status') {
        // 状态事件
        try {
          const status = JSON.parse(data)
          if (callbacks.onStatus) {
            callbacks.onStatus(status)
          }
        } catch (e) {
          console.error('解析status失败:', e)
        }
      } else if (eventType === 'sources') {
        // 来源事件
        try {
          const sources = JSON.parse(data)
          if (callbacks.onSources) {
            callbacks.onSources(sources)
          }
        } catch (e) {
          console.error('解析sources失败:', e)
        }
      } else if (eventType === 'content') {
        // 内容事件（纯文本）
        if (callbacks.onContent) {
          callbacks.onContent(data)
        }
      } else if (eventType === 'done') {
        // 完成事件
        try {
          const result = JSON.parse(data)
          if (callbacks.onDone) {
            callbacks.onDone(result)
          }
        } catch (e) {
          console.error('解析done失败:', e)
        }
      } else if (eventType === 'error') {
        // 错误事件
        try {
          const error = JSON.parse(data)
          if (callbacks.onError) {
            callbacks.onError(error.message || '未知错误')
          }
        } catch (e) {
          if (callbacks.onError) {
            callbacks.onError(data)
          }
        }
      }
    }
    
    read()
  })
  .catch(error => {
    if (callbacks.onError) {
      callbacks.onError(error.message)
    }
  })
}

/**
 * 获取会话列表
 */
export function getConversations(params) {
  return request({
    url: '/v1/chat/conversations',
    method: 'get',
    params
  })
}

/**
 * 获取会话消息
 */
export function getMessages(conversationId) {
  return request({
    url: `/v1/chat/conversations/${conversationId}/messages`,
    method: 'get'
  })
}

/**
 * 创建新会话
 */
export function createConversation(title = '新对话') {
  return request({
    url: '/v1/chat/conversations',
    method: 'post',
    params: { title }
  })
}

/**
 * 删除会话
 */
export function deleteConversation(conversationId) {
  return request({
    url: `/v1/chat/conversations/${conversationId}`,
    method: 'delete'
  })
}

/**
 * 更新会话标题
 */
export function updateConversationTitle(conversationId, title) {
  return request({
    url: `/v1/chat/conversations/${conversationId}`,
    method: 'put',
    params: { title }
  })
}
