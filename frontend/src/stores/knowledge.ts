import { defineStore } from 'pinia'
import { knowledgeApi } from '@/api'
import type { KnowledgeDoc, KnowledgeCategory, KnowledgeDocRequest } from '@/types'
import { message } from 'ant-design-vue'

interface KnowledgeState {
  documents: KnowledgeDoc[]
  categories: KnowledgeCategory[]
  currentDoc: KnowledgeDoc | null
  total: number
  page: number
  size: number
  isLoading: boolean
}

export const useKnowledgeStore = defineStore('knowledge', {
  state: (): KnowledgeState => ({
    documents: [],
    categories: [],
    currentDoc: null,
    total: 0,
    page: 1,
    size: 10,
    isLoading: false,
  }),

  actions: {
    // 获取文档列表
    async fetchDocuments(params?: {
      category?: string
      status?: string
      keyword?: string
      page?: number
      size?: number
    }) {
      try {
        this.isLoading = true
        const res = await knowledgeApi.getDocuments({
          page: this.page,
          size: this.size,
          ...params,
        })
        const pageResult = res.data
        this.documents = pageResult.list
        this.total = pageResult.total
        this.page = pageResult.page
        this.size = pageResult.size
        return true
      } catch (error) {
        console.error('Fetch documents error:', error)
        return false
      } finally {
        this.isLoading = false
      }
    },

    // 上传文档
    async uploadDocument(file: File, data: KnowledgeDocRequest) {
      try {
        const res = await knowledgeApi.uploadDocument(file, data)
        message.success('文档上传成功，正在处理中...')
        this.documents.unshift(res.data)
        return res.data
      } catch (error) {
        console.error('Upload document error:', error)
        return null
      }
    },

    // 获取文档详情
    async fetchDocument(docId: number) {
      try {
        this.isLoading = true
        const res = await knowledgeApi.getDocument(docId)
        this.currentDoc = res.data
        return res.data
      } catch (error) {
        console.error('Fetch document error:', error)
        return null
      } finally {
        this.isLoading = false
      }
    },

    // 更新文档
    async updateDocument(docId: number, data: KnowledgeDocRequest) {
      try {
        await knowledgeApi.updateDocument(docId, data)
        const doc = this.documents.find((d) => d.id === docId)
        if (doc) {
          Object.assign(doc, data)
        }
        if (this.currentDoc?.id === docId) {
          Object.assign(this.currentDoc, data)
        }
        message.success('文档更新成功')
        return true
      } catch (error) {
        console.error('Update document error:', error)
        return false
      }
    },

    // 删除文档
    async deleteDocument(docId: number) {
      try {
        await knowledgeApi.deleteDocument(docId)
        this.documents = this.documents.filter((d) => d.id !== docId)
        if (this.currentDoc?.id === docId) {
          this.currentDoc = null
        }
        message.success('文档已删除')
        return true
      } catch (error) {
        console.error('Delete document error:', error)
        return false
      }
    },

    // 重新索引文档
    async reindexDocument(docId: number) {
      try {
        await knowledgeApi.reindexDocument(docId)
        message.success('重新索引已启动')
        return true
      } catch (error) {
        console.error('Reindex document error:', error)
        return false
      }
    },

    // 获取分类列表
    async fetchCategories() {
      try {
        const res = await knowledgeApi.getCategories()
        this.categories = res.data
        return true
      } catch (error) {
        console.error('Fetch categories error:', error)
        return false
      }
    },

    // 设置分页
    setPage(page: number) {
      this.page = page
    },

    setSize(size: number) {
      this.size = size
    },
  },
})
