<template>
  <div class="order-list-page">
    <Navbar />
    <div class="main-content">
      <h2 class="page-title">我的订单</h2>
      <el-tabs v-model="activeTab" @tab-change="fetchOrders">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="待付款" name="pending" />
        <el-tab-pane label="待发货" name="shipping" />
        <el-tab-pane label="待收货" name="delivering" />
        <el-tab-pane label="已完成" name="completed" />
        <el-tab-pane label="已取消" name="cancelled" />
      </el-tabs>

      <div v-loading="loading" class="order-list">
        <div v-for="order in orders" :key="order.id" class="order-card">
          <div class="order-header" @click="$router.push(`/order/${order.id}`)">
            <span class="order-no">订单号 {{ order.orderNo }}</span>
            <el-tag :type="statusType(order.status)" size="small">{{ statusLabel(order.status) }}</el-tag>
          </div>
          <div v-for="item in order.items" :key="item.id" class="order-item" @click="$router.push(`/order/${order.id}`)">
            <el-image :src="item.productImage || item.image" class="item-img" />
            <div class="item-info">
              <p class="item-name">{{ item.productName }}</p>
              <p v-if="item.specDesc" class="item-spec">{{ item.specDesc }}</p>
            </div>
            <span class="item-price">¥{{ Number(item.price).toFixed(2) }}</span>
            <span class="item-qty">x{{ item.quantity }}</span>
          </div>
          <div class="order-footer">
            <span class="order-time">{{ formatTime(order.createTime) }}</span>
            <span class="order-total">
              共{{ order.items?.length || 0 }} 件
              合计: <strong>¥{{ Number(order.totalAmount || order.totalPrice || 0).toFixed(2) }}</strong>
            </span>
            <div class="order-actions" @click.stop>
              <el-button v-if="order.status === 1" type="danger" plain size="small" @click.stop="cancelShipped(order)">取消订单</el-button>
              <el-button v-if="canRefund(order.status)" type="warning" size="small" @click.stop="openRefundDialog(order)">申请退款</el-button>
            </div>
          </div>
        </div>
        <el-empty v-if="!orders.length && !loading" description="暂无订单" />
      </div>

      <div class="pagination-wrap" v-if="total > 0">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="fetchOrders"
        />
      </div>
    </div>
    <Footer />
    <RefundDialog v-model="refundDialogVisible" :order="refundTarget" @success="onRefundSuccess" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import Navbar from '@/components/Navbar.vue'
import Footer from '@/components/Footer.vue'
import RefundDialog from '@/components/RefundDialog.vue'
import { getOrderList, cancelOrder } from '@/api/order'

const orders = ref([])
const loading = ref(false)
const activeTab = ref('all')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const refundDialogVisible = ref(false)
const refundTarget = ref(null)

const statusMap = {
  0: { label: '待付款', type: 'warning', key: 'pending' },
  1: { label: '待发货', type: 'primary', key: 'shipping' },
  2: { label: '待收货', type: 'info', key: 'delivering' },
  3: { label: '已完成', type: 'success', key: 'completed' },
  4: { label: '已取消', type: 'danger', key: 'cancelled' },
  5: { label: '已退款', type: 'danger', key: 'refunded' }
}

function statusType(status) {
  return statusMap[status]?.type || 'info'
}
function statusLabel(status) {
  return statusMap[status]?.label || '未知'
}
function statusKey(status) {
  return statusMap[status]?.key || ''
}

function formatTime(t) {
  if (!t) return ''
  const d = typeof t === 'string' ? new Date(t) : t
  if (isNaN(d.getTime())) return String(t)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function fetchOrders() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      pageSize: pageSize.value
    }
    if (activeTab.value !== 'all') {
      // 把字符串 key 转回数字
      const reverse = { pending: 0, shipping: 1, delivering: 2, completed: 3, cancelled: 4 }
      params.status = reverse[activeTab.value] ?? activeTab.value
    }
    const res = await getOrderList(params)
    orders.value = res.data?.records || res.data?.list || res.data || []
    total.value = res.data?.total || 0
  } catch {
    orders.value = []
  } finally {
    loading.value = false
  }
}

// 可退款：已完成(3) / 待收货(2) 都可以
function canRefund(status) {
  return status === 2 || status === 3
}

function openRefundDialog(order) {
  refundTarget.value = order
  refundDialogVisible.value = true
}

function onRefundSuccess() {
  // 刷新当前列表
  fetchOrders()
}

async function cancelShipped(order) {
  try {
    await ElMessageBox.confirm(
      '该订单已付款，取消后将进入退款流程，款项会在 3~7 个工作日内原路返回。继续吗？',
      '取消已付款订单',
      { type: 'warning' }
    )
    await cancelOrder(order.id)
    ElMessage.success('已取消订单并提交退款')
    fetchOrders()
  } catch {}
}

onMounted(fetchOrders)
</script>

<style scoped>
.order-list-page { min-height: 100vh; background: #f5f7fa; }
.main-content { max-width: 1200px; margin: 0 auto; padding: 20px; }
.page-title { font-size: 22px; margin: 0 0 20px; }
.order-list { display: flex; flex-direction: column; gap: 16px; }
.order-card { background: #fff; border-radius: 8px; padding: 16px 20px; cursor: pointer; transition: box-shadow 0.2s; }
.order-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.order-header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 12px; border-bottom: 1px solid #f0f0f0; margin-bottom: 12px; }
.order-no { font-size: 13px; color: #909399; }
.order-item { display: flex; align-items: center; gap: 12px; padding: 8px 0; }
.item-img { width: 50px; height: 50px; border-radius: 6px; }
.item-info { flex: 1; }
.item-name { margin: 0; font-size: 14px; }
.item-price { color: #f56c6c; }
.item-qty { color: #909399; }
.order-footer { display: flex; justify-content: space-between; align-items: center; padding-top: 12px; border-top: 1px solid #f0f0f0; margin-top: 12px; font-size: 13px; color: #909399; }
.order-total strong { color: #f56c6c; font-size: 16px; }
.order-actions { display: flex; gap: 8px; }
.pagination-wrap { display: flex; justify-content: center; padding: 30px 0; }
</style>