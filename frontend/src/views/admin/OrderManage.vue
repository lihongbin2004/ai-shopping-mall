<template>
  <div class="admin-layout">
    <AdminSidebar />
    <div class="admin-main">
      <AdminHeader title="订单管理" />
      <div class="admin-content">
        <el-card>
          <div class="toolbar">
            <el-select v-model="statusFilter" placeholder="订单状态" clearable style="width: 130px" @change="fetchOrders">
              <el-option label="待付款" :value="0" />
              <el-option label="待发货" :value="1" />
              <el-option label="待收货" :value="2" />
              <el-option label="已完成" :value="3" />
              <el-option label="已取消" :value="4" />
              <el-option label="已退款" :value="5" />
            </el-select>
            <el-input v-model="orderNo" placeholder="订单号" clearable style="width: 180px" />
            <el-button type="primary" @click="fetchOrders">搜索</el-button>
          </div>
          <el-table :data="orders" v-loading="loading" stripe border>
            <el-table-column prop="orderNo" label="订单号" width="180" />
            <el-table-column label="用户" width="120">
              <template #default="{ row }">{{ row.username || row.userId }}</template>
            </el-table-column>
            <el-table-column label="商品数" width="80">
              <template #default="{ row }">{{ row.items?.length || 0 }}</template>
            </el-table-column>
            <el-table-column label="总金额" width="110">
              <template #default="{ row }">¥{{ (row.totalAmount || row.totalPrice || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="支付金额" width="110">
              <template #default="{ row }">¥{{ (row.payAmount || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :formatter="(row) => formatTime(row.createTime)" prop="createTime" label="下单时间" width="170" />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openDetail(row)">查看</el-button>
                <el-button
                  v-if="row.status === 1"
                  size="small"
                  type="success"
                  @click="handleShip(row)"
                >确认发货</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="page"
              :page-size="pageSize"
              :total="total"
              layout="total, prev, pager, next"
              @current-change="fetchOrders"
            />
          </div>
        </el-card>
      </div>
    </div>

    <!-- 订单详情弹窗（不进用户"我的订单"） -->
    <el-dialog v-model="detailVisible" title="订单详情" width="720px" top="5vh">
      <div v-if="current" class="order-detail">
        <div class="status-banner" :class="'status-' + current.status">
          <el-icon size="20" style="margin-right:8px"><Box /></el-icon>
          <span>{{ statusLabel(current.status) }}</span>
        </div>
        <el-descriptions :column="2" border size="small" style="margin-top:16px">
          <el-descriptions-item label="订单号">{{ current.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ current.username || current.userId }}</el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ formatTime(current.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="支付方式">{{ current.payment || '—' }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ formatTime(current.payTime) }}</el-descriptions-item>
          <el-descriptions-item label="收货人">{{ current.addressName }} {{ current.addressPhone }}</el-descriptions-item>
          <el-descriptions-item :span="2" label="收货地址">{{ current.addressDetail || '—' }}</el-descriptions-item>
        </el-descriptions>

        <h4 style="margin: 20px 0 10px">商品信息</h4>
        <el-table :data="current.items || []" border size="small">
          <el-table-column label="商品" min-width="220">
            <template #default="{ row: item }">
              <div style="display:flex;align-items:center;gap:10px">
                <el-image
                  v-if="item.productImage"
                  :src="item.productImage"
                  style="width:48px;height:48px;border-radius:4px"
                  fit="cover"
                />
                <span>{{ item.productName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="单价" width="100">
            <template #default="{ row: item }">¥{{ (item.price || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="70" />
          <el-table-column label="小计" width="100">
            <template #default="{ row: item }">¥{{ ((item.price || 0) * (item.quantity || 1)).toFixed(2) }}</template>
          </el-table-column>
        </el-table>

        <div class="total-row">
          <div>商品金额：<b>¥{{ (current.totalAmount || current.totalPrice || 0).toFixed(2) }}</b></div>
          <div>实付金额：<b style="color:#f56c6c">¥{{ (current.payAmount || 0).toFixed(2) }}</b></div>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="current && current.status === 1"
          type="success"
          @click="handleShip(current); detailVisible = false"
        >确认发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Box } from '@element-plus/icons-vue'
import AdminSidebar from './components/AdminSidebar.vue'
import AdminHeader from './components/AdminHeader.vue'
import { getAdminOrderList, updateOrderStatus, getAdminOrderDetail } from '@/api/admin'

const orders = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const statusFilter = ref('')
const orderNo = ref('')
const detailVisible = ref(false)
const current = ref(null)

const statusMap = {
  0: { label: '待付款', type: 'warning' },
  1: { label: '待发货', type: 'primary' },
  2: { label: '待收货', type: 'info' },
  3: { label: '已完成', type: 'success' },
  4: { label: '已取消', type: 'danger' },
  5: { label: '已退款', type: 'danger' }
}
function statusType(s) { return statusMap[s]?.type || 'info' }
function statusLabel(s) { return statusMap[s]?.label || s }
function formatTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  if (isNaN(d.getTime())) return t
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function fetchOrders() {
  loading.value = true
  try {
    const params = { page: page.value, pageSize: pageSize.value }
    if (statusFilter.value !== '' && statusFilter.value !== null) params.status = statusFilter.value
    if (orderNo.value) params.orderNo = orderNo.value
    const res = await getAdminOrderList(params)
    orders.value = res.data?.records || res.data?.list || res.data || []
    total.value = res.data?.total || 0
  } catch {} finally {
    loading.value = false
  }
}

async function openDetail(row) {
  detailVisible.value = true
  current.value = row
  try {
    // 拉取完整订单详情（包含 items、address、receiver）
    const res = await getAdminOrderDetail(row.id)
    if (res.data) current.value = { ...row, ...res.data }
  } catch (e) {
    // 失败就只用列表行数据
  }
}

async function handleShip(row) {
  try {
    await ElMessageBox.confirm(`确认将订单 ${row.orderNo} 标记为已发货？`, '发货确认', { type: 'success' })
    await updateOrderStatus({ id: row.id, status: 2 })
    ElMessage.success('已确认发货')
    fetchOrders()
  } catch {}
}

onMounted(fetchOrders)
</script>

<style scoped>
.admin-layout { display: flex; min-height: 100vh; background: #f0f2f5; }
.admin-main { flex: 1; display: flex; flex-direction: column; }
.admin-content { padding: 20px; flex: 1; }
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.pagination-wrap { display: flex; justify-content: center; margin-top: 16px; }
.order-detail .status-banner {
  display: flex; align-items: center; padding: 14px 20px;
  color: #fff; border-radius: 8px; font-size: 16px; font-weight: 500;
}
.status-banner.status-0 { background: linear-gradient(90deg, #ffb84d, #ff7a45); }
.status-banner.status-1 { background: linear-gradient(90deg, #4dc6ff, #1eb8ff); }
.status-banner.status-2 { background: linear-gradient(90deg, #67c23a, #409eff); }
.status-banner.status-3 { background: linear-gradient(90deg, #85ce61, #67c23a); }
.status-banner.status-4, .status-banner.status-5 { background: linear-gradient(90deg, #f56c6c, #c45656); }
.total-row { text-align: right; margin-top: 16px; line-height: 1.8; }
</style>