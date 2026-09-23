<template>
  <div class="register-page">
    <div class="register-container">
      <div class="register-header">
        <router-link to="/home" class="back-link">
          <el-icon><ArrowLeft /></el-icon> 返回首页
        </router-link>
        <h2>创建账号</h2>
        <p>加入AI智能购物商城，开启智能购物之旅</p>
      </div>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        label-width="100px"
        class="register-form"
        @submit.prevent="handleRegister"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="请再次输入密码" show-password />
        </el-form-item>
        <!-- 图形数字验证码 -->
        <el-form-item label="验证码" prop="code">
          <div class="code-row">
            <el-input v-model="form.code" placeholder="请输入验证码" maxlength="4" />
            <div class="captcha-box" @click="refreshCaptcha">
              <canvas ref="captchaCanvas" width="120" height="40"></canvas>
            </div>
            <el-button type="text" @click="refreshCaptcha">换一张</el-button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="agree">
            我已阅读并同意
            <a href="javascript:;">《用户协议》</a>和
            <a href="javascript:;">《隐私政策》</a>
          </el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" class="submit-btn">
            注册
          </el-button>
        </el-form-item>
      </el-form>
      <div class="form-footer">
        <span>已有账号？</span>
        <router-link to="/login">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)
const agree = ref(false)
const captchaCanvas = ref(null)
const captchaCode = ref('')

const form = reactive({
  username: '',
  phone: '',
  email: '',
  password: '',
  confirmPassword: '',
  code: ''
})

// 生成随机数字验证码
function generateCaptcha() {
  const canvas = captchaCanvas.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  
  // 生成4位随机数字
  let code = ''
  for (let i = 0; i < 4; i++) {
    code += Math.floor(Math.random() * 10)
  }
  captchaCode.value = code

  // 绘制验证码
  ctx.clearRect(0, 0, 120, 40)
  
  // 背景色
  ctx.fillStyle = '#f0f4ff'
  ctx.fillRect(0, 0, 120, 40)
  
  // 绘制数字
  const colors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399']
  for (let i = 0; i < 4; i++) {
    const fontSize = 20 + Math.random() * 8
    ctx.font = `${fontSize}px Arial`
    ctx.fillStyle = colors[Math.floor(Math.random() * colors.length)]
    ctx.textBaseline = 'middle'
    
    const x = 10 + i * 25 + Math.random() * 5
    const y = 20 + (Math.random() - 0.5) * 10
    const rotation = (Math.random() - 0.5) * 0.4
    
    ctx.save()
    ctx.translate(x, y)
    ctx.rotate(rotation)
    ctx.fillText(code[i], 0, 0)
    ctx.restore()
  }
  
  // 绘制干扰线
  ctx.strokeStyle = '#dcdfe6'
  ctx.lineWidth = 1
  for (let i = 0; i < 3; i++) {
    ctx.beginPath()
    ctx.moveTo(Math.random() * 120, Math.random() * 40)
    ctx.lineTo(Math.random() * 120, Math.random() * 40)
    ctx.stroke()
  }
  
  // 绘制噪点
  for (let i = 0; i < 20; i++) {
    ctx.fillStyle = colors[Math.floor(Math.random() * colors.length)]
    ctx.beginPath()
    ctx.arc(Math.random() * 120, Math.random() * 40, 1, 0, 2 * Math.PI)
    ctx.fill()
  }
}

function refreshCaptcha() {
  generateCaptcha()
}

const validatePass = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validatePass, trigger: 'blur' }
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

async function handleRegister() {
  if (!agree.value) {
    ElMessage.warning('请阅读并同意用户协议和隐私政策')
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  
  // 校验图形验证码
  if (form.code !== captchaCode.value) {
    ElMessage.error('验证码错误')
    refreshCaptcha()
    form.code = ''
    return
  }
  
  loading.value = true
  try {
    await userStore.register(form)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  generateCaptcha()
})
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 40px 20px;
}
.register-container {
  width: 520px;
  background: #fff;
  border-radius: 16px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3);
}
.register-header {
  text-align: center;
  margin-bottom: 30px;
  position: relative;
}
.back-link {
  position: absolute;
  left: 0;
  top: 0;
  text-decoration: none;
  color: #409eff;
  font-size: 14px;
}
.register-header h2 {
  font-size: 24px;
  color: #303133;
  margin: 0 0 8px;
}
.register-header p {
  color: #909399;
  margin: 0;
}
.register-form {
  max-width: 400px;
  margin: 0 auto;
}
.code-row {
  display: flex;
  gap: 10px;
  width: 100%;
  align-items: center;
}
.code-row .el-input {
  flex: 1;
}
.captcha-box {
  cursor: pointer;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  line-height: 0;
}
.captcha-box canvas {
  display: block;
  border-radius: 4px;
}
.submit-btn {
  width: 100%;
}
.form-footer {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
  color: #909399;
}
.form-footer a {
  color: #409eff;
  text-decoration: none;
}
a {
  color: #409eff;
}
</style>
