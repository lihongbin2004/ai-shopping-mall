<template>
  <div class="order-detail-page">
    <Navbar />
    <div class="main-content" v-loading="loading">
      <div class="breadcrumb">
        <el-breadcrumb>
          <el-breadcrumb-item :to="{ path: '/orders' }">我的订单</el-breadcrumb-item>
          <el-breadcrumb-item>订单详情</el-breadcrumb-item>
        </el-breadcrumb>
      </div>

      <div v-if="order.id" class="order-detail">
        <div class="status-bar" :class="'status-' + statusKey">
          <el-icon :size="40"><component :is="statusIcon" /></el-icon>
          <div class="status-text">
            <h3>{{ statusLabel(statusKey) }}</h3>
            <p>{{ statusDesc }}</p>
          </div>
        </div>

        <!-- 物流地图：发货后显示 -->
        <LogisticsMap
          v-if="order.id && ['shipping','delivering','completed','refunded','cancelled'].includes(statusKey)"
          :order-id="order.id"
        />

        <div class="info-section">
          <h4>收货信息</h4>
          <p>{{ order.addressName }} {{ order.addressPhone }}</p>
          <p>{{ order.addressDetail }}</p>
        </div>

        <div class="info-section">
          <h4>订单信息</h4>
          <p>订单号: {{ order.orderNo }}</p>
          <p>下单时间: {{ formatTime(order.createTime) }}</p>
          <p>支付方式: {{ order.payment }}</p>
          <p v-if="order.payTime">付款时间: {{ formatTime(order.payTime) }}</p>
          <p v-if="order.finishTime">完成时间: {{ formatTime(order.finishTime) }}</p>
          <p v-if="order.remark">订单备注: {{ order.remark }}</p>
        </div>

        <div class="info-section">
          <h4>商品信息</h4>
          <div v-for="item in order.items" :key="item.id" class="order-item">
            <el-image :src="item.productImage || item.image" class="item-img" />
            <div class="item-info">
              <p class="item-name">{{ item.productName }}</p>
              <p v-if="item.specDesc" class="item-spec">{{ item.specDesc }}</p>
            </div>
            <span class="item-price">¥{{ Number(item.price).toFixed(2) }}</span>
            <span class="item-qty">x{{ item.quantity }}</span>
            <span class="item-total">¥{{ (Number(item.price) * item.quantity).toFixed(2) }}</span>
          </div>
        </div>

        <div class="amount-summary">
          <div class="amount-row">
            <span>商品金额</span>
            <span>¥{{ Number(order.totalAmount).toFixed(2) }}</span>
          </div>
          <div class="amount-row total">
            <span>实付金额</span>
            <span class="red">¥{{ Number(order.totalAmount).toFixed(2) }}</span>
          </div>
        </div>

        <el-alert
          v-if="statusKey === 'pending'"
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 20px;"
        >
          <template #title>
            <strong>支付说明</strong>
          </template>
          <template #default>
            <p><strong style="color:#67c23a">建议直接点击"模拟支付（推荐）"</strong>，一键完成支付，<strong>沙箱网关经常 502/超时</strong>，避免等待。</p>
            <p style="margin-top:6px">如需走完整支付宝流程，点"打开支付宝沙箱"，会在新窗口打开沙箱支付页。</p>
            <p style="margin-top:6px">沙箱账号 <code>yfqbkc5946@sandbox.com</code>，密码 <code>111111</code>，支付密码 <code>111111</code>。</p>
          </template>
        </el-alert>

        <div class="action-bar">
          <el-button v-if="statusKey === 'pending'" type="success" @click="mockPayOrder" :loading="mockLoading">
            模拟支付（推荐）
          </el-button>
          <el-button v-if="statusKey === 'pending'" type="primary" @click="payOrder" :loading="payLoading">
            打开支付宝沙箱
          </el-button>
          <el-button v-if="statusKey === 'pending'" plain @click="payOrder" :loading="payLoading">
            沙箱报错？再试一次
          </el-button>
          <el-button v-if="statusKey === 'pending'" @click="handleCancel">
            取消订单
          </el-button>
          <el-button v-if="statusKey === 'shipping'" type="info" disabled>
            等待商家发货
          </el-button>
          <el-button v-if="statusKey === 'shipping'" type="danger" plain @click="handleCancelShipped">
            取消订单（申请退款）
          </el-button>
          <el-button v-if="statusKey === 'delivering'" type="success" @click="handleConfirm">
            确认收货
          </el-button>
          <el-button v-if="statusKey === 'completed'" @click="handleReview">
            去评价
          </el-button>
          <el-button v-if="statusKey === 'cancelled'" text @click="handleDelete">
            删除订单
          </el-button>
        </div>
      </div>
    </div>
    <Footer />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import Navbar from '@/components/Navbar.vue'
import Footer from '@/components/Footer.vue'
import LogisticsMap from '@/components/LogisticsMap.vue'
import { getOrderDetail, cancelOrder, createAlipayPay, mockPay, confirmOrder, deleteOrder, queryPayStatus } from '@/api/order'

const route = useRoute()
const router = useRouter()
const order = ref({})
const loading = ref(false)
const payLoading = ref(false)
const payRetries = ref(0)
const mockLoading = ref(false)

// 状态码 → 字符串
const STATUS_MAP = {
  0: 'pending',
  1: 'shipping',
  2: 'delivering',
  3: 'completed',
  4: 'cancelled',
  5: 'refunded'
}
const statusKey = computed(() => STATUS_MAP[order.value.status] || 'pending')

const statusIcon = computed(() => {
  const map = { pending: 'Wallet', shipping: 'Box', delivering: 'Van', completed: 'CircleCheck', cancelled: 'CircleClose', refunded: 'RefreshLeft' }
  return map[statusKey.value] || 'InfoFilled'
})

const statusDesc = computed(() => {
  const map = {
    pending: '请尽快完成付款，逾期订单将自动取消',
    shipping: '商家正在备货中，请耐心等待',
    delivering: '商品正在配送中，请注意查收',
    completed: '订单已完成，感谢您的购买',
    cancelled: '订单已取消',
    refunded: '订单已退款'
  }
  return map[statusKey.value] || ''
})

const statusMap = {
  pending: '待付款', shipping: '待发货', delivering: '待收货',
  completed: '已完成', cancelled: '已取消', refunded: '已退款'
}
function statusLabel(s) { return statusMap[s] || s }

function formatTime(t) {
  if (!t) return ''
  // 后端返回 ISO 字符串（UTC），统一转成 Date 对象
  const d = typeof t === 'string' ? new Date(t) : t
  if (isNaN(d.getTime())) return String(t)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function fetchDetail() {
  loading.value = true
  try {
    const res = await getOrderDetail(route.params.id)
    order.value = res.data || {}
  } catch {
    ElMessage.error('订单加载失败')
  } finally {
    loading.value = false
  }
}

// 同步订单支付状态：从支付宝主动查询，已支付则更新本地状态
async function syncPayStatus(showTip = false) {
  if (!order.value?.id) return false
  try {
    const res = await queryPayStatus(order.value.id)
    if (res.data?.paid) {
      await fetchDetail()
      if (showTip) ElMessage.success('支付成功，订单已更新')
      return true
    }
  } catch (e) {
    console.warn('[OrderDetail] 同步支付状态失败:', e)
  }
  return false
}

async function payOrder() {
  payLoading.value = true
  payRetries.value = 0
  // 设置8秒后兜底询问：沙箱经常无响应，主动询问用户是否切到模拟支付
  let fallbackTimer = setTimeout(async () => {
    if (!payLoading.value) return
    // 8s 后订单仍是待付款，弹窗询问
    try {
      await ElMessageBox.confirm(
        '沙箱网关 8 秒未响应（支付宝沙箱免费服务经常不稳定），是否切换为「模拟支付」？',
        '沙箱无响应',
        { type: 'warning', confirmButtonText: '切到模拟支付', cancelButtonText: '继续等' }
      )
      // 用户同意：调 mockPay
      payLoading.value = false
      await mockPayOrder()
    } catch {
      // 用户选择继续等
    }
  }, 8000)

  try {
    await doPay()
  } catch (e) {
    console.error('[OrderDetail] 支付发起最终失败:', e)
    const msg = e?.message || '支付发起失败'
    ElMessage({
      type: 'error',
      message: `${msg} - 请尝试"模拟支付"`,
      duration: 5000,
      showClose: true
    })
  } finally {
    clearTimeout(fallbackTimer)
    payLoading.value = false
  }
}

// 带重试的支付流程
async function doPay() {
  payRetries.value++
  console.log(`[OrderDetail] 发起支付宝沙箱支付 orderId=${order.value.id}，第 ${payRetries.value} 次`)
  const res = await createAlipayPay(order.value.id)
  console.log('[OrderDetail] 支付表单生成结果:', res)
  const form = res.data?.form
  if (!form) {
    throw new Error('支付表单生成失败，请稍后再试')
  }
  // 解析 form action 和所有 hidden input
  const wrapper = document.createElement('div')
  wrapper.innerHTML = form
  const formEl = wrapper.querySelector('form')
  if (!formEl) {
    throw new Error('支付表单解析失败')
  }
  // 关键：让 form 提交到新窗口（target=_blank），避免沙箱 502 拖死主页面
  formEl.setAttribute('target', '_blank')
  // 把 form 加到页面，再用 form.submit() 跳到支付宝网关
  wrapper.style.position = 'fixed'
  wrapper.style.left = '-9999px'
  wrapper.style.top = '0'
  document.body.appendChild(wrapper)
  console.log('[OrderDetail] 正在新窗口打开支付宝沙箱...')
  // form.submit() 会触发浏览器新窗口跳转到 form action（支付宝网关）
  formEl.submit()
  // 主页面留在原处，提示用户去新窗口完成支付
  ElMessage({
    type: 'success',
    message: '已在新窗口打开支付宝沙箱，请在新窗口完成支付',
    duration: 4000,
    showClose: true
  })
  setTimeout(() => { try { document.body.removeChild(wrapper) } catch (e) {} }, 1000)
}

async function mockPayOrder() {
  try {
    await ElMessageBox.confirm(
      '由于支付宝沙箱网关经常不稳定，此操作将直接模拟支付成功（仅用于调试）。\n是否继续？',
      '模拟支付',
      { type: 'warning', confirmButtonText: '确认模拟', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  mockLoading.value = true
  try {
    await mockPay(order.value.id)
    ElMessage.success('模拟支付成功！订单已更新')
    await fetchDetail()
  } catch (e) {
    console.error('[OrderDetail] 模拟支付失败:', e)
  } finally {
    mockLoading.value = false
  }
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm('确定要取消该订单吗？')
    await cancelOrder(order.value.id)
    ElMessage.success('订单已取消')
    fetchDetail()
  } catch {}
}

async function handleCancelShipped() {
  try {
    await ElMessageBox.confirm(
      '该订单已付款，取消后将进入退款流程，款项会在 3~7 个工作日内原路返回。继续吗？',
      '取消已付款订单',
      { type: 'warning' }
    )
    await cancelOrder(order.value.id)
    ElMessage.success('已取消订单并提交退款')
    fetchDetail()
  } catch {}
}

async function handleConfirm() {
  try {
    await ElMessageBox.confirm('确认收到商品了吗？')
    await confirmOrder(order.value.id)
    ElMessage.success('已确认收货')
    fetchDetail()
  } catch {}
}

function handleReview() {
  // 从订单商品中取第一个商品跳转评价
  const items = order.value.items || order.value.orderItems || []
  if (items.length > 0 && items[0].productId) {
    router.push(`/product/${items[0].productId}`)
  } else {
    ElMessage.warning('未找到订单商品')
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确定要删除该订单吗？')
    await deleteOrder(order.value.id)
    ElMessage.success('已删除')
    router.push('/orders')
  } catch {}
}

onMounted(async () => {
  await fetchDetail()
  // 页面打开时自动同步一次（处理从支付宝返回但 notify 还没到的情况）
  if (statusKey.value === 'pending') {
    await syncPayStatus(false)
  }
})
</script>

<style scoped>
.order-detail-page {
  min-height: 100vh;
  background: #f5f7fa;
}
.main-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}
.breadcrumb { margin-bottom: 16px; }
.status-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  border-radius: 8px;
  margin-bottom: 16px;
  color: #fff;
}
.status-pending { background: linear-gradient(135deg, #f093fb, #f5576c); }
.status-shipping { background: linear-gradient(135deg, #4facfe, #00f2fe); }
.status-delivering { background: linear-gradient(135deg, #43e97b, #38f9d7); }
.status-completed { background: linear-gradient(135deg, #a8edea, #fed6e3); color: #303133; }
.status-cancelled { background: linear-gradient(135deg, #bdc3c7, #2c3e50); }
.status-refunded { background: linear-gradient(135deg, #ff9a9e, #fecfef); color: #303133; }
.status-cancelled { background: #c0c4cc; }
.status-text h3 { margin: 0 0 4px; font-size: 20px; }
.status-text p { margin: 0; font-size: 13px; opacity: 0.85; }
.info-section {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 12px;
}
.info-section h4 {
  margin: 0 0 10px;
  font-size: 15px;
  color: #303133;
}
.info-section p { margin: 4px 0; font-size: 13px; color: #606266; }
.order-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
}
.item-img { width: 50px; height: 50px; border-radius: 6px; }
.item-info { flex: 1; }
.item-name { margin: 0; font-size: 14px; }
.item-price, .item-total { color: #f56c6c; }
.item-qty { color: #909399; }
.amount-summary {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 16px;
}
.amount-row {
  display: flex;
  justify-content: flex-end;
  gap: 20px;
  font-size: 14px;
  margin-bottom: 6px;
}
.amount-row.total { font-size: 18px; font-weight: bold; }
.green { color: #67c23a; }
.red { color: #f56c6c; }
.action-bar {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
</style>
