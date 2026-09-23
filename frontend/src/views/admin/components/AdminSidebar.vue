<template>
  <div class="admin-sidebar">
    <div class="sidebar-header">
      <router-link to="/home" class="sidebar-logo">
        <el-icon :size="24" color="#fff"><Shop /></el-icon>
        <span>AI购物管理</span>
      </router-link>
    </div>
    <el-menu
      :default-active="activeMenu"
      :router="true"
      background-color="#1d1e1f"
      text-color="rgba(255,255,255,0.65)"
      active-text-color="#fff"
    >
      <el-menu-item index="/admin/dashboard">
        <el-icon><DataAnalysis /></el-icon>
        <span>控制台</span>
      </el-menu-item>
      <el-menu-item index="/admin/users">
        <el-icon><User /></el-icon>
        <span>用户管理</span>
      </el-menu-item>
      <el-menu-item index="/admin/products">
        <el-icon><Goods /></el-icon>
        <span>商品管理</span>
      </el-menu-item>
      <el-menu-item index="/admin/orders">
        <el-icon><List /></el-icon>
        <span>订单管理</span>
      </el-menu-item>
      <el-menu-item index="/admin/refunds">
        <el-icon><RefreshLeft /></el-icon>
        <span>退款管理</span>
      </el-menu-item>
      <el-menu-item index="/admin/coupons">
        <el-icon><Ticket /></el-icon>
        <span>优惠券管理</span>
      </el-menu-item>
      <el-menu-item index="/admin/config">
        <el-icon><Setting /></el-icon>
        <span>系统配置</span>
      </el-menu-item>
      <el-menu-item index="/admin/knowledge">
        <el-icon><Reading /></el-icon>
        <span>知识库管理</span>
      </el-menu-item>
      <el-menu-item index="/admin/customer-service">
        <el-icon><Service /></el-icon>
        <span>客服工作台</span>
      </el-menu-item>
    </el-menu>
    <div class="sidebar-footer">
      <el-button text style="color: rgba(255,255,255,0.5); width: 100%;" @click="backToFront">
        <el-icon><ArrowLeft /></el-icon> 返回前台
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Reading, Service } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => route.path)

function backToFront() {
  router.push('/home')
}
</script>

<style scoped>
.admin-sidebar {
  width: 220px;
  background: #1d1e1f;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.sidebar-header {
  padding: 20px 16px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}
.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}
.sidebar-footer {
  padding: 12px;
  border-top: 1px solid rgba(255,255,255,0.06);
  margin-top: auto;
}
.el-menu {
  border-right: none;
}
.el-menu-item {
  height: 48px;
  line-height: 48px;
  margin: 2px 0;
}
.el-menu-item.is-active {
  background: linear-gradient(135deg, #409eff 0%, #6366f1 100%) !important;
}
</style>
