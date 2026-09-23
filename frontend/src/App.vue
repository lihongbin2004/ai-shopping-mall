<template>
  <!-- 顶部加载条 -->
  <div v-if="isLoading" class="top-loader"></div>

  <!-- 滚动进度条 -->
  <div class="scroll-progress" :style="{ width: scrollProgress + '%' }"></div>

  <!-- 路由过渡 -->
  <router-view v-slot="{ Component, route }">
    <transition name="route-fade" mode="out-in">
      <component :is="Component" :key="route.fullPath" />
    </transition>
  </router-view>

  <!-- 全局浮动客服 / AI 助手快捷入口 -->
  <Teleport to="body">
    <CustomerServiceWidget v-if="!isAdminRoute" />
    <div v-if="showQuickHelp" class="quick-help-toast animate-slide-up" @click="goAiChat">
      <el-icon :size="18"><MagicStick /></el-icon>
      <span>有购物问题？问问 AI 助手 →</span>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { MagicStick } from '@element-plus/icons-vue'
import CustomerServiceWidget from '@/components/CustomerServiceWidget.vue'

const router = useRouter()
const route = useRoute()

const isAdminRoute = computed(() => route.path.startsWith('/admin'))

const isLoading = ref(false)
const scrollProgress = ref(0)
const showQuickHelp = ref(false)
let helpTimer = null

// 路由切换加载条
router.beforeEach((to, from) => {
  if (to.path !== from.path) isLoading.value = true
})
router.afterEach(() => {
  setTimeout(() => { isLoading.value = false }, 350)
})

// 滚动进度
function onScroll() {
  const h = document.documentElement
  const max = h.scrollHeight - h.clientHeight
  scrollProgress.value = max > 0 ? (h.scrollTop / max) * 100 : 0

  // 滚动超过 600px 显示 AI 助手提示
  if (h.scrollTop > 600 && route.path === '/home') {
    showQuickHelp.value = true
  } else {
    showQuickHelp.value = false
  }
}

function goAiChat() {
  router.push('/ai-chat')
  showQuickHelp.value = false
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  // 首次访问 5s 后弹出 AI 助手提示
  helpTimer = setTimeout(() => {
    if (route.path === '/home') showQuickHelp.value = true
    setTimeout(() => { showQuickHelp.value = false }, 6000)
  }, 5000)
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  if (helpTimer) clearTimeout(helpTimer)
})
</script>

<style>
html, body, #app {
  margin: 0;
  padding: 0;
  height: 100%;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Arial, sans-serif;
}

.quick-help-toast {
  position: fixed;
  bottom: 110px;
  right: 24px;
  z-index: 90;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 999px;
  color: #4f46e5;
  font-size: 13px;
  font-weight: 500;
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.2);
  cursor: pointer;
  transition: transform 0.25s, box-shadow 0.25s;
  user-select: none;
}
.quick-help-toast:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(99, 102, 241, 0.35);
}
</style>
