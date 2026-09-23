<template>
  <div class="pay-success-page">
    <Navbar />
    <div class="main-content">
      <el-result
        :icon="paySuccess ? 'success' : 'info'"
        :title="paySuccess ? '支付完成' : '支付处理中'"
        :sub-title="subTitle"
      >
        <template #extra>
          <el-button type="primary" @click="goOrder" :loading="loading">查看订单</el-button>
          <el-button @click="goHome">返回首页</el-button>
        </template>
      </el-result>

      <el-card v-if="orderInfo" class="order-info-card" shadow="never">
        <div class="info-row">
          <span class="label">订单号：</span>
          <span class="value">{{ orderInfo.orderNo }}</span>
        </div>
        <div class="info-row">
          <span class="label">实付金额：</span>
          <span class="value amount">¥{{ orderInfo.totalAmount }}</span>
        </div>
        <div class="info-row">
          <span class="label">订单状态：</span>
          <el-tag :type="statusTagType" effect="light">{{ statusLabel }}</el-tag>
        </div>
        <div v-if="!paySuccess" class="info-row tip">
          <el-icon><InfoFilled /></el-icon>
          <span>如未自动更新状态，请稍后刷新页面（沙箱异步通知可能有延迟）</span>
        </div>
      </el-card>
    </div>
    <Footer />
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import Navbar from '@/components/Navbar.vue'
import Footer from '@/components/Footer.vue'
import { ref, computed, onMounted } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import { getOrderByOrderNo, getOrderDetail } from '@/api/order'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const orderNo = ref(route.query.out_trade_no || '')
const orderInfo = ref(null)
const loading = ref(false)
const paySuccess = ref(false)

const statusLabel = computed(() => {
  const s = orderInfo.value?.status
  if (s === 0) return '待付款'
  if (s === 1) return '已支付'
  if (s === 2) return '已发货'
  if (s === 3) return '已完成'
  if (s === 4) return '已取消'
  return '未知'
})
const statusTagType = computed(() => {
  const s = orderInfo.value?.status
  if (s === 0) return 'warning'
  if (s === 1 || s === 2 || s === 3) return 'success'
  if (s === 4) return 'info'
  return ''
})
const subTitle = computed(() => {
  if (!orderNo.value) return '未获取到订单号'
  if (paySuccess.value) return '订单已支付成功'
  return '正在确认支付结果，请稍候...'
})

async function loadOrder() {
  if (!orderNo.value) {
    ElMessage.warning('未获取到订单号')
    return
  }
  loading.value = true
  try {
    // 1. 通过订单号查订单
    let res = await getOrderByOrderNo(orderNo.value)
    if (res.data) {
      orderInfo.value = res.data
    } else {
      // 2. 兜底：如果 out_trade_no 是数字 id
      res = await getOrderDetail(orderNo.value)
      orderInfo.value = res.data
    }
    if (orderInfo.value && orderInfo.value.status >= 1) {
      paySuccess.value = true
    } else {
      // 沙箱异步通知可能有延迟，每隔 2 秒轮询一次，最长 10 秒
      pollPayStatus()
    }
  } catch (e) {
    console.error('[PaySuccess] 加载订单失败', e)
    ElMessage.error('订单信息加载失败：' + (e?.response?.data?.msg || e?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

let pollCount = 0
async function pollPayStatus() {
  pollCount++
  if (pollCount > 5) return
  await new Promise(r => setTimeout(r, 2000))
  try {
    const res = await getOrderByOrderNo(orderNo.value)
    if (res.data && res.data.status >= 1) {
      orderInfo.value = res.data
      paySuccess.value = true
      return
    }
  } catch {}
  pollPayStatus()
}

function goOrder() {
  if (orderInfo.value?.id) {
    router.push(`/order/${orderInfo.value.id}`)
  } else {
    router.push('/orders')
  }
}
function goHome() {
  router.push('/')
}

onMounted(() => {
  loadOrder()
})
</script>

<style scoped>
.pay-success-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f0f9ff 0%, #e0e7ff 100%);
}
.main-content {
  max-width: 800px;
  margin: 0 auto;
  padding: 60px 20px;
}
.order-info-card {
  margin-top: 32px;
  border-radius: 16px;
  border: none;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}
.info-row {
  display: flex;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px dashed #e5e7eb;
}
.info-row:last-child { border-bottom: none; }
.info-row .label {
  color: #6b7280;
  width: 100px;
}
.info-row .value {
  color: #1f2937;
  font-weight: 500;
}
.info-row .value.amount {
  color: #f43f5e;
  font-size: 20px;
  font-weight: 700;
}
.info-row.tip {
  color: #6366f1;
  font-size: 13px;
  gap: 6px;
}
</style>
