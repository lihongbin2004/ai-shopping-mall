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
import { createAlipayPay, queryPayStatus } from '@/api/order'

const WELCOME_MSG = {
  role: 'assistant',
  content: '您好！我是AI购物助手，可以帮您推荐商品、解答疑问、分析需求。请问有什么可以帮您的吗？'
}

// 支付宝沙箱：把后端返回的支付表单提交到新窗口（与订单详情页一致的流程）
async function openAlipaySandbox(orderId, onPaid) {
  // 必须先同步开窗口：await 之后用户手势已失效，target=_blank 提交会被浏览器拦截
  let win = null
  try {
    win = window.open('about:blank', '_blank')
  } catch (e) {
    win = null
  }
  try {
    const res = await createAlipayPay(orderId)
    const form = res.data?.form
    if (!form) throw new Error('支付表单生成失败')
    const wrapper = document.createElement('div')
    wrapper.innerHTML = form
    const formEl = wrapper.querySelector('form')
    if (!formEl) throw new Error('支付表单解析失败')

    if (win && !win.closed) {
      // 在刚打开的空白窗口里写入并提交沙箱表单
      win.document.open()
      win.document.write(formEl.outerHTML)
      win.document.close()
      const f = win.document.querySelector('form')
      if (!f) throw new Error('支付表单写入失败')
      f.submit()
    } else {
      // 弹窗被拦截时的兜底：表单写进当前页面并提交到新窗口
      formEl.setAttribute('target', '_blank')
      wrapper.style.position = 'fixed'
      wrapper.style.left = '-9999px'
      wrapper.style.top = '0'
      document.body.appendChild(wrapper)
      formEl.submit()
      setTimeout(() => { try { document.body.removeChild(wrapper) } catch (e) {} }, 1000)
      ElMessage({
        type: 'warning',
        message: '浏览器拦截了新窗口，请允许本站弹窗后重试',
        duration: 6000,
        showClose: true
      })
      return false
    }
    ElMessage({
      type: 'success',
      message: '已在新窗口打开支付宝沙箱，请在新窗口完成支付',
      duration: 5000,
      showClose: true
    })
    pollAlipayStatus(orderId, onPaid)
    return true
  } catch (e) {
    if (win && !win.closed) {
      try { win.close() } catch (err) { /* ignore */ }
    }
    // 沙箱网关不稳定时给出去订单详情页的兜底路径
    ElMessage({
      type: 'warning',
      message: '支付宝沙箱暂时不可用，请到「我的订单 → 订单详情」重试或使用模拟支付',
      duration: 6000,
      showClose: true
    })
    return false
  }
}

// 支付完成后主动查询几次，同步订单状态（沙箱 notify 到不了本地）
function pollAlipayStatus(orderId, onPaid) {
  let tries = 0
  const timer = setInterval(async () => {
    tries += 1
    try {
      const res = await queryPayStatus(orderId)
      if (res.data?.paid) {
        clearInterval(timer)
        ElMessage.success('支付成功，订单已更新')
        if (onPaid) onPaid()
      }
    } catch (e) { /* 忽略查询失败，继续等 */ }
    if (tries >= 20) clearInterval(timer)
  }, 3000)
}

export const useAiStore = defineStore('ai', () => {
  const messages = ref([{ ...WELCOME_MSG }])
  const loading = ref(false)
  const currentSessionId = ref(null)
  const sessions = ref([]) // 历史会话列表
  const models = ref([]) // 可用模型列表
  const currentModel = ref('auto') // 当前选中的模型，auto=按降级链
  // AI 确认支付后待付款的订单，用于在聊天页渲染"去支付宝沙箱支付"按钮
  const pendingPay = ref(null)
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

  async function sendMessage(content, images) {
    if (loading.value) return
    loading.value = true

    // 多模态：用户消息带上图片，气泡里展示缩略图
    addMessage({ role: 'user', content, images: images && images.length ? images : undefined })
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
            // 本地规则引擎的回复正文已经带了工具执行结果，只需要做副作用刷新
            const alreadyInReply = !!toolData.local
            const last = messages.value[messages.value.length - 1]
            for (const t of tools) {
              if (!last) break
              // 自动化购物流程：下单/支付完成后同步刷新购物车（下单会清空已下单的商品）
              if (t.ok && ['addCart', 'clearCart', 'createOrder', 'payOrder'].includes(t.tool)) {
                cartStore.fetchCartList().catch(() => {})
              }
              if (t.ok && t.tool === 'createOrder') {
                ElMessage.success(`下单成功，订单号 ${t.orderNo || ''}`)
              }
              if (t.ok && t.tool === 'payOrder' && t.needPay && t.orderId) {
                // 二次确认后：记录待支付订单，由用户点击按钮再打开沙箱收银台
                // （自动提交表单可能被浏览器拦截，必须依赖用户手势）
                pendingPay.value = {
                  orderId: t.orderId,
                  orderNo: t.orderNo || '',
                  totalAmount: t.totalAmount
                }
              }
              if (t.ok && t.tool === 'payOrder' && !t.needPay) {
                ElMessage.success('支付成功，商家将尽快发货')
              }
              // needConfirm = 等待用户二次确认（如支付），不属于失败，不要显示 ❌
              const text = t.needConfirm
                ? (t.displayText || '')
                : (t.ok
                  ? (t.displayText || (t.message ? `\n\n✅ ${t.message}` : '\n\n✅ 操作完成'))
                  : `\n\n❌ ${t.message || '操作失败'}`)
              if (!alreadyInReply && text && !last.content.includes(text.trim())) {
                last.content += text
              }
            }
          },
          currentModel.value,
          images
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

  // 用户点击"去支付宝沙箱支付"：必须是用户手势内调用，否则新窗口会被拦截
  async function openPendingPay() {
    const pay = pendingPay.value
    if (!pay || !pay.orderId) return false
    return openAlipaySandbox(pay.orderId, () => {
      pendingPay.value = null
    })
  }

  function dismissPendingPay() {
    pendingPay.value = null
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
    pendingPay,
    sendMessage,
    stopGeneration,
    openPendingPay,
    dismissPendingPay,
    loadSessions,
    loadModels,
    createNewSession,
    selectSession,
    removeSession,
    clearHistory,
    addMessage
  }
})
