import request from '@/utils/request'

/**
 * 上传文档
 */
export function uploadDocument(formData) {
  return request({
    url: '/v1/knowledge/docs',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 获取文档列表
 */
export function getDocuments(params) {
  return request({
    url: '/v1/knowledge/docs',
    method: 'get',
    params
  })
}

/**
 * 获取文档详情
 */
export function getDocumentById(docId) {
  return request({
    url: `/v1/knowledge/docs/${docId}`,
    method: 'get'
  })
}

/**
 * 更新文档
 */
export function updateDocument(docId, data) {
  return request({
    url: `/v1/knowledge/docs/${docId}`,
    method: 'put',
    data
  })
}

/**
 * 删除文档
 */
export function deleteDocument(docId) {
  return request({
    url: `/v1/knowledge/docs/${docId}`,
    method: 'delete'
  })
}

/**
 * 重新索引文档
 */
export function reindexDocument(docId) {
  return request({
    url: `/v1/knowledge/docs/${docId}/reindex`,
    method: 'post'
  })
}

/**
 * 获取分类列表
 */
export function getCategories() {
  return request({
    url: '/v1/knowledge/categories',
    method: 'get'
  })
}
