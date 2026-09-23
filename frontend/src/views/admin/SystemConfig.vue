<template>
  <div class="admin-layout">
    <AdminSidebar />
    <div class="admin-main">
      <AdminHeader title="系统配置" />
      <div class="admin-content">
        <el-card v-loading="loading">
          <template #header>基本设置</template>
          <el-form :model="config" label-width="120px" size="large">
            <el-form-item label="网站名称">
              <el-input v-model="config.siteName" />
            </el-form-item>
            <el-form-item label="网站描述">
              <el-input v-model="config.siteDescription" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="网站Logo">
              <el-input v-model="config.siteLogo" placeholder="Logo URL" />
            </el-form-item>
            <el-form-item label="客服电话">
              <el-input v-model="config.servicePhone" />
            </el-form-item>
            <el-form-item label="客服邮箱">
              <el-input v-model="config.serviceEmail" />
            </el-form-item>
            <el-divider />
            <div class="section-title">AI设置</div>
            <el-form-item label="AI API地址">
              <el-input v-model="config.aiApiUrl" placeholder="https://api.openai.com/v1/chat/completions" />
            </el-form-item>
            <el-form-item label="AI API Key">
              <el-input v-model="config.aiApiKey" type="password" show-password />
            </el-form-item>
            <el-form-item label="AI 模型">
              <el-input v-model="config.aiModel" placeholder="gpt-4o" />
            </el-form-item>
            <el-divider />
            <div class="section-title">订单设置</div>
            <el-form-item label="自动取消(分钟)">
              <el-input-number v-model="config.autoCancelMinutes" :min="0" />
              <span style="margin-left: 8px; color: #909399;">未付款订单超时自动取消</span>
            </el-form-item>
            <el-form-item label="自动收货(天)">
              <el-input-number v-model="config.autoConfirmDays" :min="0" />
              <span style="margin-left: 8px; color: #909399;">发货后自动确认收货</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="saveConfig">保存配置</el-button>
              <el-button @click="loadConfig">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import AdminSidebar from './components/AdminSidebar.vue'
import AdminHeader from './components/AdminHeader.vue'
import { getSystemConfig, updateSystemConfig } from '@/api/admin'

const loading = ref(false)
const saving = ref(false)

const config = reactive({
  siteName: 'AI智能购物商城',
  siteDescription: '基于AI技术的智能购物平台',
  siteLogo: '',
  servicePhone: '400-888-8888',
  serviceEmail: 'service@ai-mall.com',
  aiApiUrl: '',
  aiApiKey: '',
  aiModel: 'gpt-4o',
  autoCancelMinutes: 30,
  autoConfirmDays: 7
})

async function loadConfig() {
  loading.value = true
  try {
    const res = await getSystemConfig()
    const data = res.data || {}
    Object.keys(config).forEach(key => {
      if (data[key] !== undefined && data[key] !== null) {
        config[key] = data[key]
      }
    })
  } catch {} finally {
    loading.value = false
  }
}

async function saveConfig() {
  saving.value = true
  try {
    await updateSystemConfig({ ...config })
    ElMessage.success('配置已保存')
  } catch {} finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.admin-layout { display: flex; min-height: 100vh; background: #f0f2f5; }
.admin-main { flex: 1; display: flex; flex-direction: column; }
.admin-content { padding: 20px; flex: 1; max-width: 800px; }
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin: 8px 0 16px;
  padding-left: 8px;
  border-left: 3px solid #409eff;
}
</style>