<template>
  <div class="kf-widget">
    <!-- 未读小红点 -->
    <el-badge v-if="unread > 0 && !visible" :value="unread" :max="99" class="kf-badge">
      <el-button type="warning" circle size="large" class="kf-toggle" @click="togglePanel">
        <el-icon :size="22"><Service /></el-icon>
      </el-button>
    </el-badge>
    <el-button v-else type="warning" circle size="large" class="kf-toggle" @click="togglePanel">
      <el-icon :size="22"><Service /></el-icon>
    </el-button>

    <transition name="slide-up">
      <div v-if="visible" class="kf-panel">
        <div class="kf-header">
          <div class="header-left">
            <span class="header-icon"><el-icon><Service /></el-icon></span>
            <span class="header-title">在线客服</span>
            <span class="status-dot" :class="wsConnected ? 'online' : 'offline'"></span>
          </div>
          <div class="header-actions">
            <el-tooltip content="客服中心" placement="top">
              <el-button text size="small" @click="visible = false; $router.push('/ai-chat')">
                <el-icon><MagicStick /></el-icon>
              </el-button>
            </el-tooltip>
            <el-button text size="small" @click="visible = false">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </div>

        <div class="kf-body" ref="bodyRef">
          <!-- 连接中 -->
          <div v-if="!wsConnected && getToken()" class="system-tip">连接客服中...</div>
          <div v-else-if="!getToken()" class="system-tip">
            <p>请先登录才能联系客服</p>
            <el-button type="primary" size="small" @click="$router.push('/login')">去登录</el-button>
          </div>

          <div v-for="msg in messages" :key="msg.id || msg._tempId"
               :class="['kf-msg', msg.senderType === 0 ? 'user' : 'admin']">
            <div class="msg-avatar">{{ msg.senderType === 0 ? '我' : '客' }}</div>
            <div class="msg-content-wrap">
              <div class="msg-content">{{ msg.content }}</div>
              <div class="msg-time">{{ formatTime(msg.createTime) }}</div>
            </div>
          </div>

          <div v-if="typingFrom" class="kf-msg admin">
            <div class="msg-avatar">客</div>
            <div class="msg-content-wrap">
              <div class="msg-content typing-indicator">
                <span class="dot"></span><span class="dot"></span><span class="dot"></span>
              </div>
            </div>
          </div>
        </div>

        <div class="kf-input">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="2"
            :autosize="{ minRows: 1, maxRows: 3 }"
            placeholder="输入消息，回车发送..."
            :disabled="!wsConnected"
            @keydown.enter.exact.prevent="sendMsg"
            size="small"
          />
          <el-button type="warning" :disabled="!inputText.trim() || !wsConnected" @click="sendMsg">
            <el-icon><Promotion /></el-icon>
          </el-button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Service, Close, Promotion, MagicStick } from '@element-plus/icons-vue'
import { getToken } from '@/utils/auth'

const visible = ref(false)
const wsConnected = ref(false)
const unread = ref(0)
const inputText = ref('')
const messages = ref([])
const typingFrom = ref(null)
const bodyRef = ref(null)

let ws = null
let sessionId = null

function togglePanel() {
  visible.value = !visible.value
  if (visible.value && getToken()) {
    unread.value = 0
    loadHistory()
  }
}

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
    ws.onopen = () => { wsConnected.value = true; console.log('[KF] connected') }
    ws.onclose = () => { wsConnected.value = false; setTimeout(connect, 3000) }
    ws.onerror = () => { wsConnected.value = false }
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
    case 'connected':
      break
    case 'message':
      sessionId = data.sessionId
      messages.value.push(data)
      typingFrom.value = null
      if (!visible.value) unread.value++
      nextTick(scrollBottom)
      break
    case 'typing':
      if (data.role === 1) typingFrom.value = true
      break
    case 'close':
      ElMessage.info('会话已关闭')
      break
  }
}

function sendMsg() {
  const text = inputText.value.trim()
  if (!text || !wsConnected.value) return
  ws.send(JSON.stringify({ type: 'message', content: text, sessionId }))
  inputText.value = ''
}

async function loadHistory() {
  if (!sessionId) {
    // 先拿活跃会话
    try {
      const res = await fetch('/api/kf/session', {
        headers: { 'Authorization': 'Bearer ' + getToken() }
      })
      const data = await res.json()
      if (data.data) {
        sessionId = data.data.id
        messages.value = []
      }
    } catch {}
  }
  if (sessionId) {
    try {
      const res = await fetch('/api/kf/messages?sessionId=' + sessionId, {
        headers: { 'Authorization': 'Bearer ' + getToken() }
      })
      const data = await res.json()
      messages.value = data.data || []
      nextTick(scrollBottom)
    } catch {}
  }
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

// 监听可见性，关闭时标记已读
watch(visible, (v) => {
  if (v && sessionId && wsConnected.value) {
    ws.send(JSON.stringify({ type: 'read', sessionId }))
  }
})

onMounted(() => { if (getToken()) connect() })
onUnmounted(() => { if (ws) ws.close() })
</script>

<style scoped>
.kf-widget { position: fixed; right: 20px; bottom: 80px; z-index: 2000; }
.kf-toggle { width: 48px; height: 48px; box-shadow: 0 4px 12px rgba(234, 88, 12, 0.4); }
.kf-badge { position: absolute; top: -4px; right: -4px; }

.kf-panel {
  position: fixed; right: 20px; bottom: 140px;
  width: 380px; height: 560px;
  background: #fff; border-radius: 16px;
  box-shadow: 0 12px 48px rgba(0,0,0,0.18);
  display: flex; flex-direction: column; overflow: hidden;
}
.kf-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 18px; background: linear-gradient(135deg, #f97316, #ea580c);
  color: #fff;
}
.header-left { display: flex; align-items: center; gap: 8px; }
.header-title { font-weight: 600; font-size: 15px; }
.status-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.status-dot.online { background: #22c55e; box-shadow: 0 0 6px #22c55e; }
.status-dot.offline { background: #94a3b8; }
.header-actions { display: flex; gap: 4px; }
.header-actions .el-button { color: #fff !important; }

.kf-body {
  flex: 1; overflow-y: auto; padding: 14px 16px;
  background: #f8fafc;
}
.system-tip { text-align: center; color: #64748b; font-size: 13px; padding: 30px 0; }

.kf-msg { display: flex; gap: 8px; margin-bottom: 12px; }
.kf-msg.user { flex-direction: row-reverse; }
.msg-avatar {
  width: 32px; height: 32px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 600; color: #fff; flex-shrink: 0;
}
.kf-msg.user .msg-avatar { background: #f97316; }
.kf-msg.admin .msg-avatar { background: #6366f1; }
.msg-content-wrap { max-width: 72%; }
.msg-content {
  padding: 10px 14px; border-radius: 12px;
  font-size: 14px; line-height: 1.5; word-break: break-word;
}
.kf-msg.user .msg-content { background: #f97316; color: #fff; border-top-right-radius: 4px; }
.kf-msg.admin .msg-content { background: #fff; color: #1f2937; border-top-left-radius: 4px; border: 1px solid #e2e8f0; }
.msg-time { font-size: 11px; color: #94a3b8; margin-top: 4px; }
.kf-msg.user .msg-time { text-align: right; }

.typing-indicator { display: flex; gap: 4px; padding: 8px 12px; }
.typing-indicator .dot { width: 6px; height: 6px; border-radius: 50%; background: #94a3b8; animation: blink 1.2s infinite; }
.typing-indicator .dot:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink { 0%,80%,100% { opacity: 0.3; } 40% { opacity: 1; } }

.kf-input {
  display: flex; gap: 8px; padding: 12px; border-top: 1px solid #f1f5f9;
  background: #fff;
}
.kf-input .el-button { flex-shrink: 0; }

.slide-up-enter-active, .slide-up-leave-active { transition: all 0.25s ease; }
.slide-up-enter-from { opacity: 0; transform: translateY(20px); }
.slide-up-leave-to { opacity: 0; transform: translateY(20px); }
</style>
