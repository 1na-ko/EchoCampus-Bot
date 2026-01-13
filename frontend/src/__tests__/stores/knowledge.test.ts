import { describe, it, expect, beforeEach, vi, afterEach, Mock } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useKnowledgeStore } from '@/stores/knowledge'
import { knowledgeApi } from '@/api'

// Mock API模块
vi.mock('@/api', () => ({
  knowledgeApi: {
    getDocuments: vi.fn(),
    uploadDocument: vi.fn(),
    getDocument: vi.fn(),
    updateDocument: vi.fn(),
    deleteDocument: vi.fn(),
    reindexDocument: vi.fn(),
    getCategories: vi.fn(),
  },
}))

// Mock ant-design-vue message
vi.mock('ant-design-vue', () => ({
  message: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    warning: vi.fn(),
  },
}))

/**
 * Knowledge Store 单元测试
 * P3 优先级 - 前端状态管理
 */
describe('Knowledge Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('初始状态', () => {
    it('应该有正确的初始状态', () => {
      const store = useKnowledgeStore()

      expect(store.documents).toEqual([])
      expect(store.categories).toEqual([])
      expect(store.currentDoc).toBeNull()
      expect(store.total).toBe(0)
      expect(store.page).toBe(1)
      expect(store.size).toBe(10)
      expect(store.isLoading).toBe(false)
    })
  })

  describe('fetchDocuments action', () => {
    it('获取文档列表成功应该更新状态', async () => {
      const store = useKnowledgeStore()
      const mockPageResult = {
        total: 2,
        page: 1,
        size: 10,
        list: [
          { id: 1, title: '文档1', fileType: 'pdf', status: 'ACTIVE' },
          { id: 2, title: '文档2', fileType: 'docx', status: 'ACTIVE' },
        ],
      }

      ;(knowledgeApi.getDocuments as Mock).mockResolvedValueOnce({
        data: mockPageResult,
      })

      const result = await store.fetchDocuments()

      expect(result).toBe(true)
      expect(store.documents).toHaveLength(2)
      expect(store.documents[0].title).toBe('文档1')
      expect(store.total).toBe(2)
      expect(store.page).toBe(1)
      expect(store.size).toBe(10)
      expect(store.isLoading).toBe(false)
    })

    it('获取文档列表失败应该返回false', async () => {
      const store = useKnowledgeStore()

      ;(knowledgeApi.getDocuments as Mock).mockRejectedValueOnce(new Error('网络错误'))

      const result = await store.fetchDocuments()

      expect(result).toBe(false)
      expect(store.documents).toEqual([])
      expect(store.isLoading).toBe(false)
    })

    it('应该支持传入查询参数', async () => {
      const store = useKnowledgeStore()
      const mockPageResult = { total: 1, page: 2, size: 20, list: [] }

      ;(knowledgeApi.getDocuments as Mock).mockResolvedValueOnce({
        data: mockPageResult,
      })

      await store.fetchDocuments({
        category: '技术文档',
        status: 'ACTIVE',
        keyword: '测试',
        page: 2,
        size: 20,
      })

      expect(knowledgeApi.getDocuments).toHaveBeenCalledWith({
        page: 2,
        size: 20,
        category: '技术文档',
        status: 'ACTIVE',
        keyword: '测试',
      })
    })

    it('应该使用store中的默认分页参数', async () => {
      const store = useKnowledgeStore()
      store.page = 3
      store.size = 15
      const mockPageResult = { total: 0, page: 3, size: 15, list: [] }

      ;(knowledgeApi.getDocuments as Mock).mockResolvedValueOnce({
        data: mockPageResult,
      })

      await store.fetchDocuments()

      expect(knowledgeApi.getDocuments).toHaveBeenCalledWith({
        page: 3,
        size: 15,
      })
    })
  })

  describe('uploadDocument action', () => {
    it('上传文档成功应该添加到列表', async () => {
      const store = useKnowledgeStore()
      const mockDoc = {
        id: 1,
        title: '新文档',
        fileName: 'test.pdf',
        fileType: 'pdf',
        status: 'ACTIVE',
        processStatus: 'PENDING',
      }
      const file = new File(['test'], 'test.pdf', { type: 'application/pdf' })
      const data = { title: '新文档', description: '描述' }

      ;(knowledgeApi.uploadDocument as Mock).mockResolvedValueOnce({
        data: mockDoc,
      })

      const result = await store.uploadDocument(file, data)

      expect(result).not.toBeNull()
      expect(result?.id).toBe(1)
      expect(store.documents).toContainEqual(mockDoc)
      expect(store.documents[0]).toBe(mockDoc) // 应该添加到列表开头
    })

    it('上传文档失败应该返回null', async () => {
      const store = useKnowledgeStore()
      const file = new File(['test'], 'test.exe', { type: 'application/x-msdownload' })

      ;(knowledgeApi.uploadDocument as Mock).mockRejectedValueOnce(new Error('不支持的文件类型'))

      const result = await store.uploadDocument(file, { title: '测试' })

      expect(result).toBeNull()
    })
  })

  describe('fetchDocument action', () => {
    it('获取文档详情成功应该设置currentDoc', async () => {
      const store = useKnowledgeStore()
      const mockDoc = {
        id: 1,
        title: '详情文档',
        fileType: 'pdf',
        content: '内容',
      }

      ;(knowledgeApi.getDocument as Mock).mockResolvedValueOnce({
        data: mockDoc,
      })

      const result = await store.fetchDocument(1)

      expect(result).not.toBeNull()
      expect(result?.id).toBe(1)
      expect(store.currentDoc).toEqual(mockDoc)
      expect(store.isLoading).toBe(false)
    })

    it('获取文档详情失败应该返回null', async () => {
      const store = useKnowledgeStore()

      ;(knowledgeApi.getDocument as Mock).mockRejectedValueOnce(new Error('文档不存在'))

      const result = await store.fetchDocument(999)

      expect(result).toBeNull()
      expect(store.currentDoc).toBeNull()
      expect(store.isLoading).toBe(false)
    })
  })

  describe('updateDocument action', () => {
    it('更新文档成功应该更新本地状态', async () => {
      const store = useKnowledgeStore()
      store.documents = [
        { id: 1, title: '旧标题', description: '旧描述' } as any,
        { id: 2, title: '其他文档', description: '描述' } as any,
      ]
      store.currentDoc = { id: 1, title: '旧标题' } as any

      ;(knowledgeApi.updateDocument as Mock).mockResolvedValueOnce({ data: {} })

      const result = await store.updateDocument(1, { title: '新标题', description: '新描述' })

      expect(result).toBe(true)
      expect(store.documents.find((d) => d.id === 1)?.title).toBe('新标题')
      expect(store.currentDoc?.title).toBe('新标题')
    })

    it('更新不在列表中的文档应该只发送请求', async () => {
      const store = useKnowledgeStore()
      store.documents = [{ id: 2, title: '其他文档' } as any]

      ;(knowledgeApi.updateDocument as Mock).mockResolvedValueOnce({ data: {} })

      const result = await store.updateDocument(1, { title: '新标题' })

      expect(result).toBe(true)
      expect(knowledgeApi.updateDocument).toHaveBeenCalledWith(1, { title: '新标题' })
    })

    it('更新文档失败应该返回false', async () => {
      const store = useKnowledgeStore()

      ;(knowledgeApi.updateDocument as Mock).mockRejectedValueOnce(new Error('更新失败'))

      const result = await store.updateDocument(1, { title: '新标题' })

      expect(result).toBe(false)
    })
  })

  describe('deleteDocument action', () => {
    it('删除文档成功应该从列表中移除', async () => {
      const store = useKnowledgeStore()
      store.documents = [
        { id: 1, title: '文档1' } as any,
        { id: 2, title: '文档2' } as any,
      ]
      store.currentDoc = { id: 1, title: '文档1' } as any

      ;(knowledgeApi.deleteDocument as Mock).mockResolvedValueOnce({ data: {} })

      const result = await store.deleteDocument(1)

      expect(result).toBe(true)
      expect(store.documents).toHaveLength(1)
      expect(store.documents[0].id).toBe(2)
      expect(store.currentDoc).toBeNull() // 当前文档被删除后应该清空
    })

    it('删除非当前文档不应该清空currentDoc', async () => {
      const store = useKnowledgeStore()
      store.documents = [
        { id: 1, title: '文档1' } as any,
        { id: 2, title: '文档2' } as any,
      ]
      store.currentDoc = { id: 1, title: '文档1' } as any

      ;(knowledgeApi.deleteDocument as Mock).mockResolvedValueOnce({ data: {} })

      await store.deleteDocument(2)

      expect(store.currentDoc).not.toBeNull()
      expect(store.currentDoc?.id).toBe(1)
    })

    it('删除文档失败应该返回false', async () => {
      const store = useKnowledgeStore()
      store.documents = [{ id: 1, title: '文档1' } as any]

      ;(knowledgeApi.deleteDocument as Mock).mockRejectedValueOnce(new Error('删除失败'))

      const result = await store.deleteDocument(1)

      expect(result).toBe(false)
      expect(store.documents).toHaveLength(1) // 列表不应该改变
    })
  })

  describe('reindexDocument action', () => {
    it('重新索引成功应该返回true', async () => {
      const store = useKnowledgeStore()

      ;(knowledgeApi.reindexDocument as Mock).mockResolvedValueOnce({ data: {} })

      const result = await store.reindexDocument(1)

      expect(result).toBe(true)
      expect(knowledgeApi.reindexDocument).toHaveBeenCalledWith(1)
    })

    it('重新索引失败应该返回false', async () => {
      const store = useKnowledgeStore()

      ;(knowledgeApi.reindexDocument as Mock).mockRejectedValueOnce(new Error('重新索引失败'))

      const result = await store.reindexDocument(1)

      expect(result).toBe(false)
    })
  })

  describe('fetchCategories action', () => {
    it('获取分类列表成功应该更新状态', async () => {
      const store = useKnowledgeStore()
      const mockCategories = [
        { id: 1, name: '技术文档' },
        { id: 2, name: '产品文档' },
      ]

      ;(knowledgeApi.getCategories as Mock).mockResolvedValueOnce({
        data: mockCategories,
      })

      const result = await store.fetchCategories()

      expect(result).toBe(true)
      expect(store.categories).toHaveLength(2)
      expect(store.categories[0].name).toBe('技术文档')
    })

    it('获取分类列表失败应该返回false', async () => {
      const store = useKnowledgeStore()

      ;(knowledgeApi.getCategories as Mock).mockRejectedValueOnce(new Error('获取失败'))

      const result = await store.fetchCategories()

      expect(result).toBe(false)
      expect(store.categories).toEqual([])
    })
  })

  describe('分页控制', () => {
    it('setPage 应该更新页码', () => {
      const store = useKnowledgeStore()

      store.setPage(5)

      expect(store.page).toBe(5)
    })

    it('setSize 应该更新每页大小', () => {
      const store = useKnowledgeStore()

      store.setSize(20)

      expect(store.size).toBe(20)
    })
  })

  describe('边界条件测试', () => {
    it('应该处理空的文档列表', async () => {
      const store = useKnowledgeStore()
      const mockPageResult = { total: 0, page: 1, size: 10, list: [] }

      ;(knowledgeApi.getDocuments as Mock).mockResolvedValueOnce({
        data: mockPageResult,
      })

      await store.fetchDocuments()

      expect(store.documents).toEqual([])
      expect(store.total).toBe(0)
    })

    it('应该处理空的分类列表', async () => {
      const store = useKnowledgeStore()

      ;(knowledgeApi.getCategories as Mock).mockResolvedValueOnce({ data: [] })

      await store.fetchCategories()

      expect(store.categories).toEqual([])
    })

    it('更新文档时currentDoc为null不应该报错', async () => {
      const store = useKnowledgeStore()
      store.documents = [{ id: 1, title: '文档1' } as any]
      store.currentDoc = null

      ;(knowledgeApi.updateDocument as Mock).mockResolvedValueOnce({ data: {} })

      const result = await store.updateDocument(1, { title: '新标题' })

      expect(result).toBe(true)
      expect(store.documents[0].title).toBe('新标题')
    })
  })
})
