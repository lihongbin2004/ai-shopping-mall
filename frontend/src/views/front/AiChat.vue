<template>
  <div class="ai-chat-page">
    <div class="chat-container">
      <div class="chat-sidebar">
        <div class="sidebar-header">
          <el-icon :size="24" color="#fff"><MagicStick /></el-icon>
          <h2>AI购物助手</h2>
        </div>

        <el-button
          type="primary"
          class="new-chat-btn"
          :icon="Plus"
          @click="handleNewChat"
        >
          新建对话
        </el-button>

        <div class="sidebar-section-title">AI 模型</div>
        <el-select
          v-model="aiStore.currentModel"
          placeholder="选择模型"
          size="small"
          class="model-select"
        >
          <el-option label="自动（按降级链）" value="auto" />
          <el-option
            v-for="m in aiStore.models"
            :key="m.provider"
            :label="`${m.label}${m.enabled ? '' : ' (未配置)'}`"
            :value="m.provider"
            :disabled="!m.enabled"
          />
        </el-select>

        <div class="sidebar-section-title">历史会话</div>
        <div class="session-list">
          <div v-if="aiStore.sessions.length === 0" class="session-empty">
            暂无历史对话
          </div>
          <div
            v-for="s in aiStore.sessions"
            :key="s.id"
            :class="['session-item', { active: aiStore.currentSessionId === s.id }]"
            @click="selectSession(s.id)"
          >
            <el-icon class="session-icon"><ChatDotRound /></el-icon>
            <div class="session-info">
              <div class="session-title">{{ s.title || '新对话' }}</div>
              <div class="session-time">{{ formatTime(s.updateTime || s.createTime) }}</div>
            </div>
            <el-button
              class="session-del"
              text
              size="small"
              :icon="Delete"
              @click.stop="confirmDelete(s)"
            />
          </div>
        </div>

        <div class="sidebar-bottom">
          <el-button text class="action-btn" @click="handleClear">
            <el-icon><Delete /></el-icon> 清空所有
          </el-button>
          <el-button text class="action-btn" @click="$router.push('/home')">
            <el-icon><HomeFilled /></el-icon> 返回首页
          </el-button>
        </div>
      </div>

      <div class="chat-main">
        <div class="chat-messages" ref="msgRef">
          <div
            v-for="(msg, i) in aiStore.messages"
            :key="i"
            :class="['message', msg.role === 'user' ? 'user' : 'assistant']"
          >
            <div class="msg-avatar">
              <el-avatar v-if="msg.role === 'assistant'" :size="36" style="background: linear-gradient(135deg, #409eff, #6366f1)">
                <el-icon><MagicStick /></el-icon>
              </el-avatar>
              <el-avatar v-else :size="36" style="background: #409eff">
                <el-icon><User /></el-icon>
              </el-avatar>
            </div>
            <div class="msg-content-wrapper">
              <div class="msg-role">{{ msg.role === 'assistant' ? 'AI助手' : '我' }}</div>
              <!-- 多模态：用户上传的图片 -->
              <div v-if="msg.images && msg.images.length" class="msg-images">
                <el-image
                  v-for="(img, idx) in msg.images"
                  :key="idx"
                  :src="img"
                  :preview-src-list="msg.images"
                  :initial-index="idx"
                  fit="cover"
                  class="msg-image"
                  preview-teleported
                />
              </div>
              <div class="msg-content" v-html="renderContent(msg.content)"></div>
            </div>
          </div>
          <div v-if="aiStore.loading" class="message assistant">
            <div class="msg-avatar">
              <el-avatar :size="36" style="background: linear-gradient(135deg, #409eff, #6366f1)">
                <el-icon><MagicStick /></el-icon>
              </el-avatar>
            </div>
            <div class="msg-content-wrapper">
              <div class="msg-role">AI助手</div>
              <div class="typing-indicator">
                <span class="dot"></span><span class="dot"></span><span class="dot"></span>
              </div>
            </div>
          </div>
        </div>
        <!-- AI 自动化购物：确认支付后，由用户点击按钮打开支付宝沙箱收银台 -->
        <div v-if="aiStore.pendingPay" class="pay-bar">
          <div class="pay-bar-info">
            <el-icon :size="18"><Wallet /></el-icon>
            <span>
              待支付订单 <b>{{ aiStore.pendingPay.orderNo }}</b>，应付
              <b class="pay-amount">¥{{ aiStore.pendingPay.totalAmount }}</b>
            </span>
          </div>
          <div class="pay-bar-actions">
            <el-button type="primary" size="small" :loading="payOpening" @click="handleOpenPay">
              去支付宝沙箱支付
            </el-button>
            <el-button text size="small" @click="aiStore.dismissPendingPay()">稍后支付</el-button>
          </div>
        </div>
        <div class="chat-input-bar">
          <!-- 多模态：待发送图片预览 -->
          <div v-if="pendingImages.length" class="pending-images">
            <div v-for="(img, idx) in pendingImages" :key="idx" class="pending-item">
              <el-image :src="img" fit="cover" class="pending-image" />
              <el-icon class="pending-remove" @click="removeImage(idx)"><Close /></el-icon>
            </div>
          </div>
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="2"
            placeholder="输入消息，按 Enter 发送；点 📷 可以让 AI 看图片推荐商品..."
            :disabled="aiStore.loading"
            @keyup.enter.prevent="sendMessage"
          />
          <div class="input-actions">
            <el-tooltip :content="micTooltip" placement="top">
              <el-button
                :class="{ 'mic-active': listening }"
                text
                :disabled="aiStore.loading || !recognitionSupported"
                @click="toggleVoiceInput"
              >
                <el-icon :size="18"><Microphone /></el-icon>
              </el-button>
            </el-tooltip>
            <el-button
              text
              :disabled="aiStore.loading || pendingImages.length >= 3"
              title="上传图片（最多 3 张）"
              @click="triggerPickImage"
            >
              <el-icon :size="18"><Picture /></el-icon>
            </el-button>
            <input
              ref="fileInputRef"
              type="file"
              accept="image/*"
              multiple
              style="display: none"
              @change="onFileChange"
            />
            <el-button
              v-if="aiStore.loading"
              type="danger"
              @click="aiStore.stopGeneration()"
            >
              <el-icon><VideoPause /></el-icon> 停止
            </el-button>
            <el-button
              v-else
              type="primary"
              :disabled="!inputText.trim() && !pendingImages.length"
              @click="sendMessage"
            >
              <el-icon><Promotion /></el-icon> 发送
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import {
  Plus,
  Delete,
  ChatDotRound,
  HomeFilled,
  MagicStick,
  User,
  VideoPause,
  Promotion,
  Picture,
  Close,
  Microphone,
  Wallet
} from '@element-plus/icons-vue'
import { useAiStore } from '@/stores/ai'

const router = useRouter()
const aiStore = useAiStore()
const inputText = ref('')
const msgRef = ref(null)
// 多模态：待发送的图片（dataURL）
const fileInputRef = ref(null)
const pendingImages = ref([])
// 支付宝沙箱支付按钮的 loading（点击后生成支付表单并提交到新窗口）
const payOpening = ref(false)

// 语音输入：浏览器原生 Web Speech API（Chrome/Edge 支持，Safari 部分支持）
const recognitionSupported = ref(false)
const listening = ref(false)
const micTooltip = ref('语音输入')
let recognition = null
// 开始录音时输入框里已有的文字，识别结果接在它后面
let voiceBaseText = ''

function initVoiceInput() {
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SR) {
    recognitionSupported.value = false
    micTooltip.value = '当前浏览器不支持语音输入，请用 Chrome 或 Edge'
    return
  }
  recognitionSupported.value = true
  recognition = new SR()
  recognition.lang = 'zh-CN'
  recognition.continuous = true
  recognition.interimResults = true
  recognition.maxAlternatives = 1

  recognition.onstart = () => {
    listening.value = true
    micTooltip.value = '正在聆听…点一下结束'
  }

  recognition.onresult = (event) => {
    let finalText = ''
    let interimText = ''
    for (let i = 0; i < event.results.length; i++) {
      const result = event.results[i]
      const transcript = result[0] && result[0].transcript ? result[0].transcript : ''
      if (result.isFinal) finalText += transcript
      else interimText += transcript
    }
    inputText.value = voiceBaseText + finalText + interimText
  }

  recognition.onerror = (event) => {
    listening.value = false
    micTooltip.value = '语音输入'
    if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
      ElMessage.warning('麦克风权限被拒绝，请在浏览器地址栏允许麦克风')
    } else if (event.error === 'no-speech') {
      ElMessage.info('没有听到声音，请再试一次')
    } else if (event.error === 'network') {
      ElMessage.warning('语音识别服务连接失败，请检查网络或改用键盘输入')
    }
  }

  recognition.onend = () => {
    listening.value = false
    micTooltip.value = '语音输入'
  }
}

function toggleVoiceInput() {
  if (!recognition) return
  if (listening.value) {
    recognition.stop()
    return
  }
  voiceBaseText = inputText.value
  try {
    recognition.start()
  } catch (e) {
    // 连续点击时 start 可能抛 InvalidStateError，忽略即可
    console.warn('语音识别启动失败', e)
  }
}

function stopVoiceInput() {
  if (recognition && listening.value) {
    recognition.stop()
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (msgRef.value) {
      msgRef.value.scrollTop = msgRef.value.scrollHeight
    }
  })
}

// 初始化时拉模型列表
onMounted(() => {
  aiStore.loadModels()
  initVoiceInput()
})

function triggerPickImage() {
  if (aiStore.loading) return
  if (pendingImages.value.length >= 3) {
    ElMessage.warning('最多上传 3 张图片')
    return
  }
  fileInputRef.value && fileInputRef.value.click()
}

function removeImage(idx) {
  pendingImages.value.splice(idx, 1)
}

/**
 * 读取图片并压缩为 dataURL：最长边 1000px、JPEG 0.8
 * 目的：控制请求体大小（base64 会膨胀约 33%），避免 SSE 传输过慢
 */
function fileToCompressedDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onerror = () => reject(new Error('读取图片失败'))
    reader.onload = () => {
      const img = new Image()
      img.onerror = () => reject(new Error('图片解析失败'))
      img.onload = () => {
        const max = 1000
        let { width, height } = img
        if (width > max || height > max) {
          const scale = Math.min(max / width, max / height)
          width = Math.round(width * scale)
          height = Math.round(height * scale)
        }
        const canvas = document.createElement('canvas')
        canvas.width = width
        canvas.height = height
        const ctx = canvas.getContext('2d')
        ctx.drawImage(img, 0, 0, width, height)
        resolve(canvas.toDataURL('image/jpeg', 0.8))
      }
      img.src = reader.result
    }
    reader.readAsDataURL(file)
  })
}

async function onFileChange(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  for (const file of files) {
    if (pendingImages.value.length >= 3) break
    if (!file.type.startsWith('image/')) {
      ElMessage.warning('只能上传图片文件')
      continue
    }
    if (file.size > 10 * 1024 * 1024) {
      ElMessage.warning('图片不能超过 10MB')
      continue
    }
    try {
      const dataUrl = await fileToCompressedDataUrl(file)
      pendingImages.value.push(dataUrl)
    } catch (err) {
      ElMessage.error(err.message || '图片处理失败')
    }
  }
}

async function sendMessage() {
  const text = inputText.value.trim()
  const images = pendingImages.value.slice()
  if ((!text && !images.length) || aiStore.loading) return
  stopVoiceInput() // 发送时停止收音，避免识别结果持续覆盖输入框
  inputText.value = ''
  pendingImages.value = []
  await aiStore.sendMessage(text, images)
  scrollToBottom()
}

// 用户点击按钮（用户手势）才打开支付宝沙箱，避免新窗口被浏览器拦截
async function handleOpenPay() {
  if (payOpening.value) return
  payOpening.value = true
  try {
    await aiStore.openPendingPay()
  } finally {
    payOpening.value = false
  }
}

// 离开页面时释放麦克风
onBeforeUnmount(() => {
  stopVoiceInput()
})

function renderContent(content) {
  if (!content) return ''
  let html = content
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
  return html
}

function handleNewChat() {
  aiStore.createNewSession()
  scrollToBottom()
}

async function selectSession(id) {
  await aiStore.selectSession(id)
  scrollToBottom()
}

async function confirmDelete(s) {
  try {
    await ElMessageBox.confirm(
      `确定要删除会话"${s.title || '新对话'}"吗？`,
      '删除会话',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await aiStore.removeSession(s.id)
    ElMessage.success('已删除')
  } catch {
    // 取消
  }
}

async function handleClear() {
  try {
    await ElMessageBox.confirm(
      '确定要清空所有会话记录吗？此操作不可恢复。',
      '清空所有',
      { type: 'warning', confirmButtonText: '清空', cancelButtonText: '取消' }
    )
    await aiStore.clearHistory()
    ElMessage.success('已清空')
  } catch {
    // 取消
  }
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return ''
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  const pad = n => (n < 10 ? '0' + n : n)
  if (sameDay) {
    return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  }
  return `${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(async () => {
  await aiStore.loadSessions()
  // 默认显示最近一个会话
  if (aiStore.sessions.length > 0 && !aiStore.currentSessionId) {
    await aiStore.selectSession(aiStore.sessions[0].id)
  }
  scrollToBottom()
})

watch(() => aiStore.messages.length, scrollToBottom)
</script>

<style scoped>
.ai-chat-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #312e81 100%);
  padding: 24px;
}
.chat-container {
  max-width: 1280px;
  margin: 0 auto;
  height: calc(100vh - 48px);
  display: flex;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.chat-sidebar {
  width: 280px;
  background: linear-gradient(180deg, #1e293b 0%, #312e81 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  padding: 20px 16px;
}
.sidebar-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.15);
}
.sidebar-header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #fff;
}

.new-chat-btn {
  width: 100%;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border: none;
  font-weight: 600;
  letter-spacing: 1px;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.4);
  transition: all 0.25s;
}
.new-chat-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(99, 102, 241, 0.6);
}

.sidebar-section-title {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
  margin: 18px 4px 8px;
  letter-spacing: 1px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  margin: 0 -8px;
  padding: 0 4px;
}
.session-list::-webkit-scrollbar {
  width: 4px;
}
.session-list::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
}

.session-empty {
  text-align: center;
  color: rgba(255, 255, 255, 0.4);
  font-size: 13px;
  padding: 24px 0;
}

.model-select {
  width: calc(100% - 24px);
  margin: 0 12px 12px 12px;
}
.model-select :deep(.el-select__wrapper) {
  background: rgba(255, 255, 255, 0.08) !important;
  box-shadow: none !important;
}
.model-select :deep(.el-select__placeholder),
.model-select :deep(.el-select__selected-item) {
  color: rgba(255, 255, 255, 0.85) !important;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: all 0.2s;
  position: relative;
}
.session-item:hover {
  background: rgba(255, 255, 255, 0.08);
}
.session-item.active {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.4), rgba(139, 92, 246, 0.4));
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
}
.session-icon {
  font-size: 16px;
  color: #a5b4fc;
  flex-shrink: 0;
}
.session-info {
  flex: 1;
  min-width: 0;
}
.session-title {
  font-size: 14px;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.session-time {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.45);
  margin-top: 2px;
}
.session-del {
  color: rgba(255, 255, 255, 0.5) !important;
  font-size: 14px;
  opacity: 0;
  transition: opacity 0.2s;
}
.session-item:hover .session-del {
  opacity: 1;
}
.session-del:hover {
  color: #f87171 !important;
}

.sidebar-bottom {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.15);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.action-btn {
  color: rgba(255, 255, 255, 0.7) !important;
  justify-content: flex-start;
}
.action-btn:hover {
  color: #fff !important;
  background: rgba(255, 255, 255, 0.08) !important;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #f8fafc;
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.message {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.message.user {
  flex-direction: row-reverse;
}
.msg-content-wrapper {
  max-width: 70%;
  background: #fff;
  padding: 12px 16px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.message.user .msg-content-wrapper {
  background: linear-gradient(135deg, #409eff, #6366f1);
  color: #fff;
}
.msg-role {
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 4px;
}
.message.user .msg-role {
  color: #cbd5e1;
}
.msg-content {
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
  white-space: pre-wrap;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}
.typing-indicator .dot {
  width: 6px;
  height: 6px;
  background: #6366f1;
  border-radius: 50%;
  animation: typing 1.2s infinite;
}
.typing-indicator .dot:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-6px); opacity: 1; }
}

/* ===== AI 支付：待支付提示条（点击后打开支付宝沙箱） ===== */
.pay-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 24px 12px;
  padding: 12px 16px;
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(254, 243, 199, 0.9), rgba(253, 230, 138, 0.6));
  border: 1px solid #fbbf24;
}
.pay-bar-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #92400e;
}
.pay-amount {
  color: #dc2626;
  font-size: 15px;
}
.pay-bar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-input-bar {
  padding: 16px 24px 20px;
  background: #fff;
  border-top: 1px solid #e2e8f0;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
}
.chat-input-bar :deep(.el-textarea__inner) {
  resize: none;
  border-radius: 10px;
  border-color: #cbd5e1;
}
.chat-input-bar :deep(.el-textarea) {
  flex: 1;
}
.input-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

/* ===== 多模态：消息气泡里的图片 ===== */
.msg-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 6px 0 8px;
}
.msg-image {
  width: 110px;
  height: 110px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  cursor: zoom-in;
}

/* ===== 多模态：输入区待发送图片预览 ===== */
.pending-images {
  display: flex;
  gap: 8px;
  width: 100%;
}
.pending-item {
  position: relative;
}
.pending-image {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  border: 1px solid #cbd5e1;
}
.pending-remove {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #ef4444;
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}

/* ===== 语音输入：正在收音时的呼吸效果 ===== */
.mic-active {
  color: #ef4444 !important;
  animation: mic-pulse 1.1s ease-in-out infinite;
}
@keyframes mic-pulse {
  0% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.18); opacity: 0.6; }
  100% { transform: scale(1); opacity: 1; }
}
</style>
