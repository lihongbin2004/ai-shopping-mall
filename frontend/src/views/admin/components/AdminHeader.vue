<template>
  <div class="admin-header">
    <div class="header-left">
      <h3>{{ title }}</h3>
    </div>
    <div class="header-right">
      <el-dropdown trigger="click">
        <span class="admin-user">
          <el-avatar :size="32">{{ userStore.nickname?.charAt(0) }}</el-avatar>
          <span>{{ userStore.nickname }}</span>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="$router.push('/profile')">
              <el-icon><User /></el-icon>个人中心
            </el-dropdown-item>
            <el-dropdown-item @click="handleLogout">
              <el-icon><SwitchButton /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

defineProps({
  title: { type: String, default: '' }
})

const router = useRouter()
const userStore = useUserStore()

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.admin-header {
  height: 56px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 1px 2px rgba(0,0,0,0.03);
}
.header-left h3 {
  margin: 0;
  font-size: 16px;
  color: #303133;
}
.admin-user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 14px;
}
</style>
