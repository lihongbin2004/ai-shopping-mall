<template>
  <div class="ai-chat-widget">
    <el-button
      v-if="!visible"
      type="primary"
      circle
      size="large"
      class="chat-toggle"
      @click="visible = true"
    >
      <el-icon :size="24"><ChatDotRound /></el-icon>
    </el-button>
    <transition name="slide-up">
      <div v-if="visible" class="chat-panel">
        <div class="chat-header">
          <span><el-icon><MagicStick /></el-icon> AI购物助手</span>
          <div class="header-actions">
            <el-button text size="small" @click="fullChat">
              <el-icon><FullScreen /></el-icon>
            </el-button>
            <el-button text size="small" @click="visible = false">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </div>
        <div class="chat-body" ref="chatBodyRef">
          <div
            v-for="(msg, i) in aiStore.messages"
            :key="i"
            :class="['message', msg.role === 'user' ? 'user-msg' : 'ai-msg']"
          >
            <div class="msg-content">{{ msg.content }}</div>
          </div>
          <div v-if="aiStore.loading" class="typing-indicator">
            <span class="dot"></span><span class="dot"></span><span class="dot"></span>
          </div>
        </div>
        <div class="chat-input">
          <el-input
            v-model="inputText"
            placeholder="输入您的问题..."
            size="small"
            :disabled="aiStore.loading"
            @keyup.enter="sendMsg"
          >
            <template #append>
              <el-button @click="sendMsg" :disabled="!inputText.trim() || aiStore.loading">
                <el-icon><Promotion /></el-icon>
              </el-button>
            </template>
          </el-input>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAiStore } from '@/stores/ai'

const router = useRouter()
const aiStore = useAiStore()
const visible = ref(false)
const inputText = ref('')
const chatBodyRef = ref(null)

async function sendMsg() {
  const text = inputText.value.trim()
  if (!text || aiStore.loading) return
  inputText.value = ''
  await aiStore.sendMessage(text)
  scrollToBottom()
}

function scrollToBottom() {
  nextTick(() => {
    if (chatBodyRef.value) {
      chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
    }
  })
}

function fullChat() {
  visible.value = false
  router.push('/ai-chat')
}

watch(() => aiStore.messages.length, scrollToBottom)
watch(() => aiStore.loading, scrollToBottom)
</script>

<style scoped>
.ai-chat-widget {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 9999;
}
.chat-toggle {
  width: 56px;
  height: 56px;
  font-size: 24px;
  box-shadow: 0 4px 12px rgba(64,158,255,0.4);
}
.chat-panel {
  width: 360px;
  height: 500px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: linear-gradient(135deg, #409eff, #6366f1);
  color: #fff;
  font-weight: 500;
}
.chat-header .el-icon {
  margin-right: 6px;
}
.header-actions {
  display: flex;
  gap: 4px;
}
.header-actions .el-button {
  color: rgba(255,255,255,0.8);
}
.chat-body {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
  background: #f5f7fa;
}
.message {
  margin-bottom: 12px;
  max-width: 85%;
}
.user-msg {
  margin-left: auto;
}
.ai-msg {
  margin-right: auto;
}
.msg-content {
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}
.user-msg .msg-content {
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 2px;
}
.ai-msg .msg-content {
  background: #fff;
  color: #303133;
  border-bottom-left-radius: 2px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
}
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 10px 14px;
  background: #fff;
  border-radius: 8px;
  width: fit-content;
}
.dot {
  width: 8px;
  height: 8px;
  background: #c0c4cc;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}
.dot:nth-child(1) { animation-delay: -0.32s; }
.dot:nth-child(2) { animation-delay: -0.16s; }
.dot:nth-child(3) { animation-delay: 0s; }
@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}
.chat-input {
  padding: 10px;
  border-top: 1px solid #eee;
}
.chat-input :deep(.el-input-group__append) {
  padding: 0;
  background: transparent;
}
.slide-up-enter-active, .slide-up-leave-active {
  transition: all 0.3s ease;
}
.slide-up-enter-from, .slide-up-leave-to {
  opacity: 0;
  transform: translateY(20px);
}
</style>