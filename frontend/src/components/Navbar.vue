<template>
  <div class="navbar" :class="{ scrolled: scrolled }">
    <div class="nav-inner">
      <div class="nav-left">
        <router-link to="/" class="logo">
          <div class="logo-icon">
            <el-icon :size="22" color="#fff"><ShoppingCart /></el-icon>
          </div>
          <span class="logo-text gradient-text">AI智能购物</span>
        </router-link>
        <div class="nav-links">
          <router-link to="/home" class="nav-link">首页</router-link>
          <router-link to="/products" class="nav-link">全部商品</router-link>
          <router-link to="/ai-chat" class="nav-link">AI助手</router-link>
          <router-link to="/customer-service" class="nav-link">在线客服</router-link>
        </div>
      </div>
      <div class="nav-right">
        <div class="search-box">
          <el-input v-model="searchKeyword" placeholder="搜索商品..." size="small" prefix-icon="Search" clearable @keyup.enter="doSearch" />
        </div>
        <router-link to="/cart" class="nav-icon-link">
          <el-badge :value="Number(cartStore.count) || 0" :hidden="!cartStore.count" class="cart-badge">
            <el-icon :size="22"><ShoppingCart /></el-icon>
          </el-badge>
        </router-link>
        <template v-if="userStore.isLoggedIn">
          <el-dropdown trigger="click">
            <span class="user-info">
              <el-avatar :size="32" :src="userStore.avatar" icon="UserFilled" />
              <span class="username">{{ userStore.nickname }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="$router.push('/profile')"><el-icon><User /></el-icon>个人中心</el-dropdown-item>
                <el-dropdown-item @click="$router.push('/orders')"><el-icon><List /></el-icon>我的订单</el-dropdown-item>
                <el-dropdown-item @click="$router.push('/favorites')"><el-icon><Star /></el-icon>我的收藏</el-dropdown-item>
                <el-dropdown-item @click="$router.push('/coupon')"><el-icon><Ticket /></el-icon>优惠券</el-dropdown-item>
                <el-dropdown-item v-if="userStore.isAdmin" @click="$router.push('/admin')"><el-icon><Setting /></el-icon>后台管理</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout"><el-icon><SwitchButton /></el-icon>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button text @click="$router.push('/login')">登录</el-button>
          <el-button type="primary" size="small" @click="$router.push('/register')">注册</el-button>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { removeToken, removeUser } from '@/utils/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const cartStore = useCartStore()
const searchKeyword = ref('')
const scrolled = ref(false)

function handleScroll() {
  scrolled.value = window.scrollY > 20
}

function doSearch() {
  const kw = searchKeyword.value.trim()
  if (kw) {
    router.push({ path: '/products', query: { keyword: kw } })
  }
}

function onSearchClear() {
  // 清除搜索框内容时，同步清空 URL 中的 keyword
  searchKeyword.value = ''
  if (route.path === '/products' && route.query.keyword) {
    const { keyword, ...rest } = route.query
    router.replace({ path: '/products', query: rest })
  }
}

// URL 中 keyword 变化时同步到搜索框
watch(() => route.query.keyword, (val) => {
  searchKeyword.value = val || ''
})

function handleLogout() {
  removeToken()
  removeUser()
  userStore.logout()
  cartStore.$reset()
  router.push('/home')
}

onMounted(() => {
  if (userStore.isLoggedIn) {
    cartStore.fetchCartCount()
  }
})
</script>

<style scoped>
.navbar {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(99, 102, 241, 0.08);
  position: sticky;
  top: 0;
  z-index: 100;
  transition: all 0.3s;
}
.navbar.scrolled {
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 4px 20px rgba(99, 102, 241, 0.08);
  border-bottom-color: rgba(99, 102, 241, 0.1);
}
.nav-inner {
  max-width: 1240px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 64px;
}
.nav-left {
  display: flex;
  align-items: center;
  gap: 36px;
}
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
}
.logo-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--gradient-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}
.logo-text {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.5px;
}
.nav-links {
  display: flex;
  gap: 20px;
}
.nav-link {
  position: relative;
  text-decoration: none;
  color: #606266;
  font-size: 15px;
  font-weight: 500;
  padding: 6px 0;
  transition: color 0.25s;
}
.nav-link::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -2px;
  width: 0;
  height: 2px;
  background: var(--gradient-primary);
  border-radius: 2px;
  transform: translateX(-50%);
  transition: width 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.nav-link:hover {
  color: #6366f1;
}
.nav-link:hover::after,
.nav-link.router-link-active::after {
  width: 100%;
}
.nav-link.router-link-active {
  color: #6366f1;
  font-weight: 600;
}
.nav-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.search-box {
  width: 220px;
  transition: width 0.3s;
}
.search-box:focus-within { width: 260px; }
.search-box :deep(.el-input__wrapper) {
  border-radius: 999px;
  background: rgba(241, 245, 249, 0.7);
  box-shadow: none;
  transition: all 0.25s;
  padding: 4px 12px;
}
.search-box :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.2);
}
.nav-icon-link {
  text-decoration: none;
  color: #606266;
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px; height: 38px;
  border-radius: 10px;
  transition: all 0.25s;
}
.nav-icon-link:hover {
  color: #6366f1;
  background: rgba(99, 102, 241, 0.08);
  transform: translateY(-1px);
}
.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}
.username {
  font-size: 14px;
  color: #606266;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>