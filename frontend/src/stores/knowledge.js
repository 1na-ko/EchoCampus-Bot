import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getDocuments, getCategories } from '@/api/knowledge'

export const useKnowledgeStore = defineStore('knowledge', () => {
  const documents = ref([])
  const categories = ref([])
  const total = ref(0)
  const loading = ref(false)

  // 加载文档列表
  async function loadDocuments(params = {}) {
    loading.value = true
    try {
      const data = await getDocuments({
        page: params.page || 1,
        size: params.size || 20,
        category: params.category,
        status: params.status,
        keyword: params.keyword
      })
      documents.value = data.list || []
      total.value = data.total || 0
    } catch (error) {
      console.error('加载文档列表失败:', error)
    } finally {
      loading.value = false
    }
  }

  // 加载分类列表
  async function loadCategories() {
    try {
      const data = await getCategories()
      categories.value = data
    } catch (error) {
      console.error('加载分类列表失败:', error)
    }
  }

  return {
    documents,
    categories,
    total,
    loading,
    loadDocuments,
    loadCategories
  }
})
