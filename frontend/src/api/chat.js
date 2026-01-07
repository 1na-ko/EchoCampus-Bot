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
