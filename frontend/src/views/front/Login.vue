<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-left">
        <div class="brand">
          <el-icon :size="48" color="#fff"><Shop /></el-icon>
          <h1>AI智能购物商城</h1>
          <p>智能推荐 · 便捷购物 · 贴心服务</p>
        </div>
      </div>
      <div class="login-right">
        <div class="login-form">
          <h2>欢迎回来</h2>
          <p class="subtitle">登录您的账户</p>
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            size="large"
            @submit.prevent="handleLogin"
          >
            <el-form-item prop="username">
              <el-input
                v-model="form.username"
                placeholder="用户名/手机号/邮箱"
                :prefix-icon="User"
              />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="密码"
                :prefix-icon="Lock"
                show-password
              />
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="remember">记住我</el-checkbox>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" native-type="submit" :loading="loading" class="submit-btn">
                登录
              </el-button>
            </el-form-item>
          </el-form>
          <div class="form-footer">
            <span>还没有账户？</span>
            <router-link to="/register">立即注册</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const cartStore = useCartStore()
const formRef = ref(null)
const loading = ref(false)
const remember = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.login(form)
    if (remember.value) {
      localStorage.setItem('saved_username', form.username)
    }
    cartStore.fetchCartCount()
    const redirect = route.query.redirect || '/home'
    router.push(redirect)
  } catch (err) {
    // Error handled in request interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.login-container { display: flex; width: 800px; min-height: 500px; background: #fff; border-radius: 16px; overflow: hidden; box-shadow: 0 20px 60px rgba(0,0,0,0.3); }
.login-left { width: 380px; background: linear-gradient(135deg, #409eff, #6366f1); display: flex; align-items: center; justify-content: center; }
.brand { text-align: center; color: #fff; padding: 40px; }
.brand h1 { font-size: 28px; margin: 16px 0 8px; }
.brand p { font-size: 14px; opacity: 0.85; margin: 0; }
.login-right { flex: 1; display: flex; align-items: center; justify-content: center; padding: 40px; }
.login-form { width: 100%; max-width: 320px; }
.login-form h2 { margin: 0 0 4px; font-size: 24px; color: #303133; }
.subtitle { color: #909399; margin: 0 0 24px; }
.submit-btn { width: 100%; }
.form-footer { text-align: center; font-size: 14px; color: #909399; }
.form-footer a { color: #409eff; text-decoration: none; }
</style>