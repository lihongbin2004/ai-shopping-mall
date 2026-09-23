<template>
  <div class="kf-admin">
    <div class="kf-header">
      <h2><el-icon><Service /></el-icon> 客服工作台</h2>
      <div class="header-right">
        <span class="status"><span class="dot" :class="wsConnected ? 'online' : 'offline'"></span>
          {{ wsConnected ? '在线服务中' : '连接中...' }}</span>
        <el-tag type="warning" effect="dark">待接入 {{ unassignedCount }}</el-tag>
      </div>
    </div>

    <div class="kf-main">
      <!-- 左侧会话列表 -->
      <div class="session-list">
        <div class="list-header">会话列表</div>
        <div
          v-for="s in sessions" :key="s.id"
          :class="['session-item', { active: currentSession?.id === s.id, unassigned: !s.adminId }]"
          @click="openSession(s)"
        >
          <div class="sess-avatar">{{ s.userName?.charAt(0) || '?' }}</div>
          <div class="sess-info">
            <div class="sess-top">
              <span class="sess-name">{{ s.userName }}</span>
              <span v-if="!s.adminId" class="badge-waiting">待接入</span>
              <span v-else-if="s.adminId !== currentAdminId" class="badge-other">他人</span>
              <span v-if="s.adminUnread > 0" class="badge-unread">{{ s.adminUnread }}</span>
            </div>
            <div class="sess-msg">{{ s.lastMessage || '暂无消息' }}</div>
            <div class="sess-time">{{ formatTime(s.lastTime || s.createTime) }}</div>
          </div>
        </div>
        <el-empty v-if="sessions.length === 0" description="暂无会话" :image-size="60" />
      </div>

      <!-- 中间聊天区 -->
      <div class="chat-area" v-if="currentSession">
        <div class="chat-header">
          <div>
            <div class="chat-title">{{ currentSession.userName }}</div>
            <div class="chat-sub">用户ID: {{ currentSession.userId }}</div>
          </div>
          <div class="chat-actions">
            <el-button v-if="!currentSession.adminId || currentSession.adminId !== currentAdminId"
              type="primary" size="small" @click="takeOver">
              {{ currentSession.adminId ? '抢接入' : '接入会话' }}
            </el-button>
            <el-button type="danger" size="small" @click="closeSession">结束会话</el-button>
          </div>
        </div>
        <div class="chat-body" ref="bodyRef">
          <div v-for="msg in messages" :key="msg.id || msg._tempId"
               :class="['kf-msg', msg.senderType === 1 ? 'admin' : 'user']">
            <div class="msg-avatar">{{ msg.senderType === 1 ? '客' : '我' }}</div>
            <div class="msg-content-wrap">
              <div class="msg-content">{{ msg.content }}</div>
              <div class="msg-time">{{ formatTime(msg.createTime) }}</div>
            </div>
          </div>
          <div v-if="typingFrom" class="kf-msg user">
            <div class="msg-avatar">客</div>
            <div class="msg-content-wrap">
              <div class="msg-content typing-indicator">
                <span class="dot"></span><span class="dot"></span><span class="dot"></span>
              </div>
            </div>
          </div>
        </div>
        <div class="chat-input">
          <el-input v-model="inputText" type="textarea" :rows="2" :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="输入回复，回车发送..." :disabled="!wsConnected"
            @keydown.enter.exact.prevent="sendMsg" />
          <el-button type="primary" :disabled="!inputText.trim() || !wsConnected" @click="sendMsg">
            发送
          </el-button>
        </div>
      </div>

      <!-- 快捷回复面板 -->
      <div class="quick-replies" v-if="currentSession">
        <div class="panel-title">快捷回复</div>
        <div class="reply-list">
          <div v-for="r in quickReplies" :key="r" class="reply-item" @click="useQuick(r)">{{ r }}</div>
        </div>
        <div class="panel-title" style="margin-top:16px">快捷动作</div>
        <div class="action-list">
          <el-button size="small" @click="$router.push('/admin/orders')">查看订单</el-button>
          <el-button size="small" @click="$router.push('/admin/refunds')">退款处理</el-button>
          <el-button size="small" @click="closeSession">结束会话</el-button>
        </div>
      </div>

      <div v-else class="chat-area empty">
        <el-empty description="选择一个会话开始服务" :image-size="100" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Service } from '@element-plus/icons-vue'
import { getToken } from '@/utils/auth'

const wsConnected = ref(false)
const sessions = ref([])
const messages = ref([])
const currentSession = ref(null)
const inputText = ref('')
const typingFrom = ref(false)
const bodyRef = ref(null)

const userStr = localStorage.getItem('ai-mall-user')
const currentAdminId = userStr ? JSON.parse(userStr).id : null

let ws = null
let pollTimer = null

const quickReplies = [
  '您好，请问有什么可以帮您？',
  '请稍等，我帮您查询一下。',
  '好的，已为您处理，请您耐心等待。',
  '非常抱歉给您带来不便，我马上帮您解决。',
  '您可以在「我的订单」中查看详情，有问题随时联系我。',
  '感谢您的咨询，祝您购物愉快！'
]

const unassignedCount = computed(() => sessions.value.filter(s => !s.adminId).length)

function getWsUrl() {
  const token = getToken()
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  const host = location.hostname === 'localhost' ? 'localhost:3000' : location.host
  return `${proto}://${host}/ws/kf?token=${token}&role=1`
}

function connect() {
  try {
    ws = new WebSocket(getWsUrl())
    ws.onopen = () => { wsConnected.value = true }
    ws.onclose = () => { wsConnected.value = false; setTimeout(connect, 3000) }
    ws.onmessage = (evt) => {
      let data
      try { data = JSON.parse(evt.data) } catch { return }
      handleWsMessage(data)
    }
  } catch (e) {
    console.error('[KF-Admin] connect fail', e)
  }
}

function handleWsMessage(data) {
  switch (data.type) {
    case 'message':
      // 更新最后消息时间（触发会话列表刷新）
      refreshSessions()
      if (currentSession.value && data.sessionId === currentSession.value.id) {
        messages.value.push(data)
        typingFrom.value = false
        nextTick(scrollBottom)
      }
      break
    case 'typing':
      if (currentSession.value && data.sessionId === currentSession.value.id && data.role === 0) {
        typingFrom.value = true
        clearTimeout(handleWsMessage._typingTimer)
        handleWsMessage._typingTimer = setTimeout(() => { typingFrom.value = false }, 3000)
      }
      break
    case 'close':
      refreshSessions()
      if (currentSession.value && data.sessionId === currentSession.value.id) {
        ElMessage.info('会话已关闭')
        currentSession.value = null
        messages.value = []
      }
      break
  }
}

async function refreshSessions() {
  try {
    const res = await fetch('/api/kf/admin/sessions', {
      headers: { 'Authorization': 'Bearer ' + getToken() }
    })
    const data = await res.json()
    sessions.value = data.data || []
  } catch {}
}

async function openSession(session) {
  currentSession.value = session
  messages.value = []
  // 接入会话
  if (!session.adminId || session.adminId !== currentAdminId) {
    try {
      await fetch(`/api/kf/admin/session/${session.id}/take`, {
        method: 'POST', headers: { 'Authorization': 'Bearer ' + getToken() }
      })
    } catch {}
  }
  // 加载历史
  try {
    const res = await fetch(`/api/kf/messages?sessionId=${session.id}`, {
      headers: { 'Authorization': 'Bearer ' + getToken() }
    })
    const data = await res.json()
    messages.value = data.data || []
    nextTick(scrollBottom)
  } catch {}
  // 标记已读
  if (wsConnected.value) {
    ws.send(JSON.stringify({ type: 'read', sessionId: session.id }))
  }
  refreshSessions()
}

async function takeOver() {
  if (!currentSession.value) return
  try {
    await fetch(`/api/kf/admin/session/${currentSession.value.id}/take`, {
      method: 'POST', headers: { 'Authorization': 'Bearer ' + getToken() }
    })
    ElMessage.success('已接入会话')
    refreshSessions()
  } catch {}
}

async function closeSession() {
  if (!currentSession.value) return
  try {
    await ElMessageBox.confirm('确定结束当前会话？', '提示', { type: 'warning' })
    if (wsConnected.value) {
      ws.send(JSON.stringify({ type: 'close', sessionId: currentSession.value.id }))
    } else {
      await fetch(`/api/kf/session/${currentSession.value.id}/close`, {
        method: 'POST', headers: { 'Authorization': 'Bearer ' + getToken() }
      })
    }
    currentSession.value = null
    messages.value = []
    refreshSessions()
    ElMessage.success('会话已结束')
  } catch {}
}

function sendMsg() {
  const text = inputText.value.trim()
  if (!text || !wsConnected.value || !currentSession.value) return
  ws.send(JSON.stringify({ type: 'message', content: text, sessionId: currentSession.value.id }))
  inputText.value = ''
}

function useQuick(text) {
  inputText.value = text
}

function scrollBottom() {
  if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(typeof t === 'number' ? t : t)
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  if (d.toDateString() === now.toDateString()) {
    return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  }
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => {
  connect()
  refreshSessions()
  pollTimer = setInterval(refreshSessions, 15000)
})
onUnmounted(() => {
  if (ws) ws.close()
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.kf-admin { padding: 0; height: calc(100vh - 60px); display: flex; flex-direction: column; }
.kf-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 24px; background: #fff; border-bottom: 1px solid #e2e8f0;
}
.kf-header h2 { margin: 0; font-size: 18px; display: flex; align-items: center; gap: 8px; }
.header-right { display: flex; gap: 16px; align-items: center; }
.status { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #64748b; }
.dot { width: 8px; height: 8px; border-radius: 50%; }
.dot.online { background: #22c55e; box-shadow: 0 0 6px #22c55e; }
.dot.offline { background: #94a3b8; }

.kf-main { flex: 1; display: flex; min-height: 0; }
.session-list {
  width: 280px; border-right: 1px solid #e2e8f0; background: #fff; overflow-y: auto;
}
.list-header { padding: 14px 16px; font-weight: 600; color: #475569; border-bottom: 1px solid #f1f5f9; font-size: 14px; }
.session-item {
  display: flex; gap: 10px; padding: 12px 16px; border-bottom: 1px solid #f8fafc;
  cursor: pointer; transition: background 0.2s;
}
.session-item:hover { background: #f8fafc; }
.session-item.active { background: #fef3c7; border-left: 3px solid #f59e0b; }
.session-item.unassigned { background: #fef2f2; }
.sess-avatar {
  width: 36px; height: 36px; border-radius: 50%; background: #6366f1; color: #fff;
  display: flex; align-items: center; justify-content: center; font-weight: 600; flex-shrink: 0;
}
.session-item.unassigned .sess-avatar { background: #ef4444; }
.sess-info { flex: 1; min-width: 0; }
.sess-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.sess-name { font-weight: 600; font-size: 14px; color: #1f2937; }
.badge-waiting { font-size: 11px; color: #ef4444; background: #fee2e2; padding: 2px 6px; border-radius: 3px; }
.badge-other { font-size: 11px; color: #6366f1; background: #e0e7ff; padding: 2px 6px; border-radius: 3px; }
.badge-unread {
  background: #ef4444; color: #fff; font-size: 11px; padding: 1px 7px; border-radius: 10px;
  min-width: 18px; text-align: center;
}
.sess-msg { font-size: 12px; color: #64748b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.sess-time { font-size: 11px; color: #94a3b8; margin-top: 2px; }

.chat-area {
  flex: 1; display: flex; flex-direction: column; background: #f8fafc; min-width: 0;
}
.chat-area.empty { display: flex; align-items: center; justify-content: center; }
.chat-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 20px; background: #fff; border-bottom: 1px solid #e2e8f0;
}
.chat-title { font-weight: 600; font-size: 15px; color: #1f2937; }
.chat-sub { font-size: 12px; color: #94a3b8; margin-top: 2px; }
.chat-actions { display: flex; gap: 8px; }
.chat-body {
  flex: 1; overflow-y: auto; padding: 16px;
  display: flex; flex-direction: column; gap: 0;
}
.kf-msg { display: flex; gap: 8px; margin-bottom: 12px; }
.kf-msg.user { flex-direction: row-reverse; }
.msg-avatar {
  width: 32px; height: 32px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 600; color: #fff; flex-shrink: 0;
}
.kf-msg.admin .msg-avatar { background: #6366f1; }
.kf-msg.user .msg-avatar { background: #f97316; }
.msg-content-wrap { max-width: 65%; }
.msg-content {
  padding: 10px 14px; border-radius: 12px;
  font-size: 14px; line-height: 1.5; word-break: break-word;
}
.kf-msg.admin .msg-content { background: #f97316; color: #fff; border-top-right-radius: 4px; }
.kf-msg.user .msg-content { background: #fff; color: #1f2937; border-top-left-radius: 4px; border: 1px solid #e2e8f0; }
.msg-time { font-size: 11px; color: #94a3b8; margin-top: 4px; }
.kf-msg.user .msg-time { text-align: right; }
.typing-indicator { display: flex; gap: 4px; padding: 8px 12px; }
.typing-indicator .dot { width: 6px; height: 6px; border-radius: 50%; background: #94a3b8; animation: blink 1.2s infinite; }
.typing-indicator .dot:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink { 0%,80%,100% { opacity: 0.3; } 40% { opacity: 1; } }

.chat-input {
  display: flex; gap: 8px; padding: 12px 16px; border-top: 1px solid #e2e8f0; background: #fff;
}

.quick-replies {
  width: 240px; border-left: 1px solid #e2e8f0; background: #fff; padding: 16px; overflow-y: auto;
}
.panel-title { font-weight: 600; font-size: 13px; color: #475569; margin-bottom: 10px; }
.reply-list { display: flex; flex-direction: column; gap: 6px; }
.reply-item {
  padding: 8px 12px; font-size: 13px; color: #475569; background: #f8fafc;
  border: 1px solid #e2e8f0; border-radius: 8px; cursor: pointer; transition: all 0.2s;
}
.reply-item:hover { background: #eef2ff; border-color: #6366f1; color: #4338ca; }
.action-list { display: flex; flex-direction: column; gap: 6px; }
</style>
