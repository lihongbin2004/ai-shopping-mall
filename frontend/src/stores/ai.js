import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  chatStream,
  clearChatHistory,
  createSession,
  getSessionList,
  deleteSession,
  getSessionMessages,
  getModels
} from '@/api/ai'
import { useCartStore } from '@/stores/cart'

const WELCOME_MSG = {
  role: 'assistant',
  content: '您好！我是AI购物助手，可以帮您推荐商品、解答疑问、分析需求。请问有什么可以帮您的吗？'
}

export const useAiStore = defineStore('ai', () => {
  const messages = ref([{ ...WELCOME_MSG }])
  const loading = ref(false)
  const currentSessionId = ref(null)
  const sessions = ref([]) // 历史会话列表
  const models = ref([]) // 可用模型列表
  const currentModel = ref('auto') // 当前选中的模型，auto=按降级链
  let abortController = null

  function addMessage(msg) {
    messages.value.push(msg)
  }

  function updateLastMessage(content) {
    const last = messages.value[messages.value.length - 1]
    if (last) {
      last.content += content
    }
  }

  function stripToolTags(content) {
    if (!content) return ''
    return content.replace(/\[\[TOOL:[\s\S]*?\[\[\/TOOL\]\]/g, '').trim()
  }

  function resetMessages() {
    messages.value = [{ ...WELCOME_MSG }]
  }

  async function sendMessage(content) {
    if (loading.value) return
    loading.value = true

    addMessage({ role: 'user', content })
    addMessage({ role: 'assistant', content: '' })

    const cartStore = useCartStore()

    return new Promise((resolve, reject) => {
      try {
        abortController = chatStream(
          content,
          currentSessionId.value,
          (chunk) => {
            const cleaned = stripToolTags(chunk)
            if (cleaned) updateLastMessage(cleaned)
          },
          () => {
            loading.value = false
            abortController = null
            const last = messages.value[messages.value.length - 1]
            if (last) last.content = stripToolTags(last.content)
            // 消息产生后刷新一下会话列表（标题可能被后端更新）
            loadSessions().catch(() => {})
            resolve()
          },
          (err) => {
            loading.value = false
            abortController = null
            updateLastMessage(`\n\n[错误: ${err.message}]`)
            reject(err)
          },
          (sessionIdFromServer) => {
            if (sessionIdFromServer) {
              const isNew = !currentSessionId.value
              currentSessionId.value = sessionIdFromServer
              if (isNew) {
                // 第一次产生的会话，主动插到列表最前面
                sessions.value.unshift({
                  id: sessionIdFromServer,
                  title: content.length > 20 ? content.slice(0, 20) + '...' : content,
                  createTime: new Date(),
                  updateTime: new Date()
                })
              }
            }
          },
          (toolData) => {
            const tools = toolData.tools || []
            for (const t of tools) {
              const last = messages.value[messages.value.length - 1]
              if (!last) continue
              if (t.ok) {
                if (['addCart', 'clearCart'].includes(t.tool)) {
                  cartStore.fetchCartList().catch(() => {})
                }
                if (t.displayText) {
                  last.content += t.displayText
                } else if (t.message) {
                  last.content += `\n\n✅ ${t.message}`
                } else {
                  last.content += `\n\n✅ 操作完成`
                }
              } else {
                last.content += `\n\n❌ ${t.message || '操作失败'}`
              }
            }
          },
          currentModel.value
        )
      } catch (err) {
        loading.value = false
        reject(err)
      }
    })
  }

  function stopGeneration() {
    if (abortController) {
      abortController.abort()
      abortController = null
      loading.value = false
    }
  }

  // 加载当前用户所有会话
  async function loadSessions() {
    try {
      const res = await getSessionList()
      const list = (res && res.data) || []
      // 按 updateTime 倒序
      list.sort((a, b) => {
        const ta = a.updateTime ? new Date(a.updateTime).getTime() : 0
        const tb = b.updateTime ? new Date(b.updateTime).getTime() : 0
        return tb - ta
      })
      sessions.value = list
    } catch (e) {
      console.warn('加载会话列表失败', e)
    }
  }

  // 新建一个空会话（前端先清空，发送消息时由后端创建）
  function createNewSession() {
    if (loading.value) return
    currentSessionId.value = null
    resetMessages()
  }

  // 切换到指定会话
  async function selectSession(id) {
    if (loading.value) {
      ElMessage?.warning?.('AI 正在生成中，请稍候再切换')
      return
    }
    if (!id || id === currentSessionId.value) return
    currentSessionId.value = id
    try {
      const res = await getSessionMessages(id)
      const list = (res && res.data) || []
      if (list.length === 0) {
        resetMessages()
      } else {
        messages.value = [
          { ...WELCOME_MSG },
          ...list.map(m => ({ role: m.role, content: m.content }))
        ]
      }
    } catch (e) {
      console.warn('加载会话消息失败', e)
      ElMessage?.error?.('加载会话消息失败')
    }
  }

  // 删除某个会话
  async function removeSession(id) {
    try {
      await deleteSession(id)
      sessions.value = sessions.value.filter(s => s.id !== id)
      if (currentSessionId.value === id) {
        currentSessionId.value = null
        resetMessages()
      }
    } catch (e) {
      console.warn('删除会话失败', e)
      ElMessage?.error?.('删除会话失败')
    }
  }

  async function clearHistory() {
    try {
      await clearChatHistory()
    } catch {
      // ignore
    }
    sessions.value = []
    currentSessionId.value = null
    resetMessages()
  }

  // 拉取可用模型列表
  async function loadModels() {
    try {
      const res = await getModels()
      if (res && res.data) {
        models.value = res.data
      }
    } catch (e) {
      // 静默失败，不阻塞主功能
      console.warn('加载模型列表失败', e)
    }
  }

  return {
    messages,
    loading,
    sessions,
    currentSessionId,
    models,
    currentModel,
    sendMessage,
    stopGeneration,
    loadSessions,
    loadModels,
    createNewSession,
    selectSession,
    removeSession,
    clearHistory,
    addMessage
  }
})
