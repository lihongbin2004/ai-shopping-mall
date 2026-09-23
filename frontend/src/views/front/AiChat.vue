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
          <el-button text class="action-btn" @click="$router.push('/knowledge')">
            <el-icon><Reading /></el-icon> 知识库
          </el-button>
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
        <div class="chat-input-bar">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="2"
            placeholder="输入消息，按 Enter 发送..."
            :disabled="aiStore.loading"
            @keyup.enter.prevent="sendMessage"
          />
          <div class="input-actions">
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
              :disabled="!inputText.trim()"
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
import { ref, nextTick, watch, onMounted } from 'vue'
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
  Reading
} from '@element-plus/icons-vue'
import { useAiStore } from '@/stores/ai'

const router = useRouter()
const aiStore = useAiStore()
const inputText = ref('')
const msgRef = ref(null)

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
})

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || aiStore.loading) return
  inputText.value = ''
  await aiStore.sendMessage(text)
  scrollToBottom()
}

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

.chat-input-bar {
  padding: 16px 24px 20px;
  background: #fff;
  border-top: 1px solid #e2e8f0;
  display: flex;
  gap: 12px;
  align-items: flex-end;
}
.chat-input-bar :deep(.el-textarea__inner) {
  resize: none;
  border-radius: 10px;
  border-color: #cbd5e1;
}
.input-actions {
  display: flex;
  gap: 8px;
}
</style>
