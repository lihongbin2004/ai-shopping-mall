<template>
  <div class="cs-page">
    <Navbar />
    <div class="cs-container">
      <div class="cs-main">
        <!-- 左侧侧边栏 -->
        <div class="cs-sidebar">
          <div class="cs-sidebar-header">
            <el-icon :size="22"><Headset /></el-icon>
            <span>在线客服</span>
          </div>
          <el-divider />
          <div class="cs-info">
            <p><el-icon><Clock /></el-icon> 服务时间: 9:00-22:00</p>
            <p><el-icon><User /></el-icon> 人工坐席实时回复</p>
          </div>
          <el-divider />
          <div class="cs-quick">
            <p class="cs-quick-title">💡 常见问题</p>
            <el-button
              v-for="(q, i) in quickQuestions"
              :key="i"
              size="small"
              class="cs-quick-btn"
              @click="askQuick(q)"
            >{{ q }}</el-button>
          </div>
          <el-divider />
          <div class="cs-status-box">
            <span class="status-dot" :class="connected ? 'online' : 'offline'"></span>
            <span>{{ connected ? '已连接在线客服' : '连接中...' }}</span>
          </div>
        </div>

        <!-- 右侧聊天区 -->
        <div class="cs-chat">
          <div class="cs-chat-header">
            <div class="cs-header-left">
              <div class="cs-header-avatar">{{ session?.adminName ? session.adminName.charAt(0).toUpperCase() : '客' }}</div>
              <div class="cs-header-info">
                <div class="cs-header-name">{{ session?.adminName || '在线客服' }}</div>
                <div class="cs-header-sub">
                  <span class="status-dot" :class="connected ? 'online' : 'offline'"></span>
                  {{ connected ? '在线为您服务' : '正在连接...' }}
                </div>
              </div>
            </div>
            <div class="cs-header-actions">
              <el-button text size="small" @click="closeSession">结束会话</el-button>
              <el-button text size="small" @click="$router.push('/home')">返回首页</el-button>
            </div>
          </div>

          <div class="cs-chat-body" ref="bodyRef">
            <div v-if="!connected && getToken()" class="system-tip">连接客服中，请稍候...</div>
            <div v-else-if="!getToken()" class="system-tip">
              <p>请先登录才能联系客服</p>
              <el-button type="primary" size="small" @click="$router.push('/login')">去登录</el-button>
            </div>

            <div v-for="msg in messages" :key="msg.id || msg._tempId"
                 :class="['cs-msg', msg.senderType === 0 ? 'self' : 'other']">
              <div class="msg-avatar">{{ msg.senderType === 0 ? '我' : '客' }}</div>
              <div class="msg-content-wrap">
                <div class="msg-bubble">{{ msg.content }}</div>
                <div class="msg-time">{{ formatTime(msg.createTime) }}</div>
              </div>
            </div>

            <div v-if="typingFrom" class="cs-msg other">
              <div class="msg-avatar">客</div>
              <div class="msg-content-wrap">
                <div class="msg-bubble typing">
                  <span class="dot"></span><span class="dot"></span><span class="dot"></span>
                </div>
              </div>
            </div>
          </div>

          <div class="cs-chat-input">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="2"
              :autosize="{ minRows: 1, maxRows: 4 }"
              placeholder="输入消息，回车发送..."
              :disabled="!connected || !getToken()"
              @keydown.enter.exact.prevent="sendMsg"
              size="default"
            />
            <el-button type="primary" :disabled="!inputText.trim() || !connected || !getToken()" @click="sendMsg">
              发送
            </el-button>
          </div>
        </div>
      </div>
    </div>
    <Footer />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { Headset, Clock, Message, User } from '@element-plus/icons-vue'
import Navbar from '@/components/Navbar.vue'
import Footer from '@/components/Footer.vue'
import { getToken } from '@/utils/auth'

const router = useRouter()

const connected = ref(false)
const session = ref(null)
const messages = ref([])
const inputText = ref('')
const typingFrom = ref(false)
const bodyRef = ref(null)

let ws = null

const quickQuestions = [
  '有什么热门商品？',
  '如何申请退款？',
  '订单多久发货？',
  '如何修改收货地址？',
  '优惠券怎么使用？',
  '如何联系商家？'
]

function getWsUrl() {
  const token = getToken()
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  const host = location.hostname === 'localhost' ? 'localhost:3000' : location.host
  return `${proto}://${host}/ws/kf?token=${token}&role=0`
}

function connect() {
  if (!getToken()) return
  try {
    ws = new WebSocket(getWsUrl())
    ws.onopen = async () => {
      connected.value = true
      await loadHistory()
    }
    ws.onclose = () => {
      connected.value = false
      setTimeout(connect, 3000)
    }
    ws.onmessage = (evt) => {
      let data
      try { data = JSON.parse(evt.data) } catch { return }
      handleWsMessage(data)
    }
  } catch (e) {
    console.error('[KF] connect fail', e)
  }
}

function handleWsMessage(data) {
  switch (data.type) {
    case 'connected': break
    case 'message':
      messages.value.push(data)
      typingFrom.value = false
      // 同步 sessionId
      if (!session.value || data.sessionId !== session.value.id) {
        session.value = { id: data.sessionId }
      }
      nextTick(scrollBottom)
      break
    case 'typing':
      if (data.role === 1) {
        typingFrom.value = true
        clearTimeout(handleWsMessage._typingTimer)
        handleWsMessage._typingTimer = setTimeout(() => { typingFrom.value = false }, 3000)
      }
      break
    case 'close':
      if (session.value && data.sessionId === session.value.id) {
        messages.value.push({
          senderType: 1, content: '会话已关闭', createTime: Date.now(),
          _system: true
        })
        session.value = null
      }
      break
  }
}

async function loadHistory() {
  try {
    const res = await fetch('/api/kf/session', {
      headers: { 'Authorization': 'Bearer ' + getToken() }
    })
    const data = await res.json()
    if (data.data) {
      session.value = data.data
      const res2 = await fetch('/api/kf/messages?sessionId=' + data.data.id, {
        headers: { 'Authorization': 'Bearer ' + getToken() }
      })
      const data2 = await res2.json()
      messages.value = data2.data || []
      nextTick(scrollBottom)
      // 标记已读
      if (ws) {
        ws.send(JSON.stringify({ type: 'read', sessionId: data.data.id }))
      }
    }
  } catch (e) {
    console.error('loadHistory fail', e)
  }
}

function sendMsg() {
  const text = inputText.value.trim()
  if (!text || !connected.value) return
  const sid = session.value?.id
  ws.send(JSON.stringify({ type: 'message', content: text, sessionId: sid }))
  inputText.value = ''
}

function askQuick(q) {
  inputText.value = q
  sendMsg()
}

async function closeSession() {
  if (!session.value) { router.push('/home'); return }
  if (ws) {
    ws.send(JSON.stringify({ type: 'close', sessionId: session.value.id }))
  }
  session.value = null
  messages.value = []
}

function scrollBottom() {
  if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(typeof t === 'number' ? t : t)
  if (isNaN(d.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => { if (getToken()) connect() })
onUnmounted(() => { if (ws) ws.close() })
</script>

<style scoped>
.cs-page { min-height: 100vh; background: linear-gradient(180deg, #f8fafc 0%, #ffffff 100%); }
.cs-container { max-width: 1200px; margin: 0 auto; padding: 24px 20px; }

.cs-main {
  display: flex; gap: 24px;
  background: #fff; border-radius: 16px; box-shadow: 0 4px 24px rgba(0,0,0,0.06);
  overflow: hidden; min-height: 600px;
}

/* 左侧边栏 */
.cs-sidebar {
  width: 240px; flex-shrink: 0; padding: 20px;
  background: linear-gradient(160deg, #fafafa 0%, #f1f5f9 100%);
  border-right: 1px solid #e2e8f0;
}
.cs-sidebar-header {
  display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 600; color: #1f2937;
}
.cs-info p { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #64748b; margin: 6px 0; }
.cs-quick-title { font-size: 13px; font-weight: 600; color: #475569; margin-bottom: 10px; }
.cs-quick-btn { margin: 4px; font-size: 12px; }
.cs-status-box {
  display: flex; align-items: center; gap: 8px; font-size: 13px; color: #475569;
}
.status-dot { width: 8px; height: 8px; border-radius: 50%; }
.status-dot.online { background: #22c55e; box-shadow: 0 0 6px #22c55e; }
.status-dot.offline { background: #94a3b8; }

/* 右侧聊天区 */
.cs-chat { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.cs-chat-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 24px; border-bottom: 1px solid #f1f5f9; background: #fff;
}
.cs-header-left { display: flex; align-items: center; gap: 12px; }
.cs-header-avatar {
  width: 40px; height: 40px; border-radius: 50%; background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-weight: 600; font-size: 16px;
}
.cs-header-name { font-weight: 600; color: #1f2937; }
.cs-header-sub { font-size: 12px; color: #64748b; display: flex; align-items: center; gap: 6px; margin-top: 2px; }
.cs-header-actions { display: flex; gap: 4px; }

.cs-chat-body {
  flex: 1; overflow-y: auto; padding: 20px 24px; background: #f8fafc;
  display: flex; flex-direction: column; gap: 0;
}
.system-tip { text-align: center; color: #64748b; padding: 40px 0; }

.cs-msg { display: flex; gap: 10px; margin-bottom: 16px; }
.cs-msg.self { flex-direction: row-reverse; }
.msg-avatar {
  width: 36px; height: 36px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 600; color: #fff;
}
.cs-msg.self .msg-avatar { background: #f97316; }
.cs-msg.other .msg-avatar { background: #6366f1; }
.msg-content-wrap { max-width: 60%; }
.msg-bubble {
  padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.6; word-break: break-word;
}
.cs-msg.self .msg-bubble { background: linear-gradient(135deg, #f97316, #ea580c); color: #fff; border-top-right-radius: 4px; }
.cs-msg.other .msg-bubble { background: #fff; color: #1f2937; border: 1px solid #e2e8f0; border-top-left-radius: 4px; }
.msg-bubble.typing { display: flex; gap: 4px; padding: 12px 14px; }
.msg-bubble.typing .dot { width: 6px; height: 6px; border-radius: 50%; background: #94a3b8; animation: blink 1.2s infinite; }
.msg-bubble.typing .dot:nth-child(2) { animation-delay: 0.2s; }
.msg-bubble.typing .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink { 0%,80%,100% { opacity: 0.3; } 40% { opacity: 1; } }
.msg-time { font-size: 11px; color: #94a3b8; margin-top: 4px; }
.cs-msg.self .msg-time { text-align: right; }

.cs-chat-input {
  display: flex; gap: 10px; padding: 14px 24px; border-top: 1px solid #e2e8f0; background: #fff;
}
.cs-chat-input .el-button { flex-shrink: 0; height: 40px; }

@media (max-width: 768px) {
  .cs-main { flex-direction: column; }
  .cs-sidebar { width: 100%; border-right: none; border-bottom: 1px solid #e2e8f0; }
}
</style>
