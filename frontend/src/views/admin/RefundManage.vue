<template>
  <div class="admin-layout">
    <AdminSidebar />
    <div class="admin-main">
      <AdminHeader title="退款管理" />
      <div class="admin-content">
        <h2 class="page-title">售后退款管理</h2>

        <div class="filter-bar">
          <el-input v-model="keyword" placeholder="搜索退款单号/订单号" clearable style="width: 240px; margin-right: 12px;" @keyup.enter="loadList" />
          <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 140px; margin-right: 12px;" @change="loadList">
            <el-option label="待审核" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="已拒绝" :value="2" />
            <el-option label="已退款" :value="3" />
          </el-select>
          <el-button type="primary" @click="loadList">查询</el-button>
        </div>

        <el-table :data="list" border stripe v-loading="loading" style="margin-top: 16px;">
          <el-table-column prop="refundNo" label="退款单号" width="200" />
          <el-table-column prop="orderNo" label="原订单号" width="200" />
          <el-table-column prop="username" label="申请人" width="100" />
          <el-table-column label="商品" min-width="180">
            <template #default="{ row }">
              <span v-if="row.productName">{{ row.productName }}</span>
              <span v-else class="text-muted">整单退款</span>
            </template>
          </el-table-column>
          <el-table-column prop="refundAmount" label="退款金额" width="110">
            <template #default="{ row }">
              <span class="amount">¥{{ Number(row.refundAmount).toFixed(2) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="退款原因" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" effect="dark" size="small">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="申请时间" width="150">
            <template #default="{ row }">
              {{ formatTime(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 0" type="success" size="small" @click="handleAudit(row, 1)">通过</el-button>
              <el-button v-if="row.status === 0" type="danger" size="small" @click="handleAudit(row, 2)">拒绝</el-button>
              <el-button v-if="row.status === 1" type="primary" size="small" @click="handleConfirmRefund(row)">确认退款</el-button>
              <el-button v-if="row.status === 2 || row.status === 3" size="small" @click="viewDetail(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          style="margin-top: 20px; justify-content: flex-end;"
          @current-change="loadList"
          @size-change="loadList"
        />

        <!-- 审核弹窗 -->
        <el-dialog v-model="auditDialogVisible" :title="auditForm.status === 1 ? '审核通过' : '审核拒绝'" width="500px">
          <el-form :model="auditForm" label-width="80px">
            <el-form-item label="退款单号">
              <span class="order-no">{{ currentRow?.refundNo }}</span>
            </el-form-item>
            <el-form-item label="退款金额">
              <span class="amount">¥{{ Number(currentRow?.refundAmount || 0).toFixed(2) }}</span>
            </el-form-item>
            <el-form-item label="退款原因">
              <span>{{ currentRow?.reason }}</span>
            </el-form-item>
            <el-form-item v-if="currentRow?.description" label="详细说明">
              <span>{{ currentRow?.description }}</span>
            </el-form-item>
            <el-form-item v-if="auditForm.status === 2" label="拒绝原因" required>
              <el-input v-model="auditForm.rejectReason" type="textarea" :rows="3" placeholder="请说明拒绝原因（必填）" />
            </el-form-item>
            <el-form-item label="客服回复">
              <el-input v-model="auditForm.adminReply" type="textarea" :rows="2" placeholder="选填，留言给用户" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="auditDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="auditSubmitting" @click="submitAudit">确认</el-button>
          </template>
        </el-dialog>

        <!-- 详情弹窗 -->
        <el-dialog v-model="detailDialogVisible" title="退款详情" width="540px">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="退款单号">{{ currentRow?.refundNo }}</el-descriptions-item>
            <el-descriptions-item label="原订单号">{{ currentRow?.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="申请人">{{ currentRow?.username }}</el-descriptions-item>
            <el-descriptions-item label="退款金额"><span class="amount">¥{{ Number(currentRow?.refundAmount || 0).toFixed(2) }}</span></el-descriptions-item>
            <el-descriptions-item label="退款原因">{{ currentRow?.reason }}</el-descriptions-item>
            <el-descriptions-item v-if="currentRow?.description" label="详细说明">{{ currentRow?.description }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTagType(currentRow?.status)" effect="dark" size="small">{{ statusText(currentRow?.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="申请时间">{{ formatTime(currentRow?.createTime) }}</el-descriptions-item>
            <el-descriptions-item v-if="currentRow?.auditTime" label="审核时间">{{ formatTime(currentRow?.auditTime) }}</el-descriptions-item>
            <el-descriptions-item v-if="currentRow?.rejectReason" label="拒绝原因">{{ currentRow?.rejectReason }}</el-descriptions-item>
            <el-descriptions-item v-if="currentRow?.adminReply" label="客服回复">{{ currentRow?.adminReply }}</el-descriptions-item>
          </el-descriptions>
        </el-dialog>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminSidebar from '@/views/admin/components/AdminSidebar.vue'
import AdminHeader from '@/views/admin/components/AdminHeader.vue'
import { getAdminRefunds, auditRefund } from '@/api/refund'

const list = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const keyword = ref('')
const statusFilter = ref(null)

const auditDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const auditSubmitting = ref(false)
const currentRow = ref(null)
const auditForm = reactive({
  status: 1,
  rejectReason: '',
  adminReply: ''
})

function statusText(status) {
  const map = { 0: '待审核', 1: '已通过', 2: '已拒绝', 3: '已退款' }
  return map[status] || '未知'
}

function statusTagType(status) {
  const map = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'info' }
  return map[status] || ''
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  if (isNaN(d.getTime())) return t
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadList() {
  loading.value = true
  try {
    const res = await getAdminRefunds({
      page: page.value,
      size: size.value,
      status: statusFilter.value,
      keyword: keyword.value || undefined
    })
    list.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (e) {
    console.error('加载退款列表失败', e)
  } finally {
    loading.value = false
  }
}

function handleAudit(row, status) {
  currentRow.value = row
  auditForm.status = status
  auditForm.rejectReason = ''
  auditForm.adminReply = ''
  auditDialogVisible.value = true
}

async function submitAudit() {
  if (auditForm.status === 2 && !auditForm.rejectReason.trim()) {
    ElMessage.warning('拒绝时必须填写拒绝原因')
    return
  }
  auditSubmitting.value = true
  try {
    await auditRefund(currentRow.value.id, {
      status: auditForm.status,
      rejectReason: auditForm.rejectReason,
      adminReply: auditForm.adminReply
    })
    ElMessage.success(auditForm.status === 1 ? '已通过审核' : '已拒绝该退款申请')
    auditDialogVisible.value = false
    await loadList()
  } catch (e) {
    console.error('审核失败', e)
  } finally {
    auditSubmitting.value = false
  }
}

async function handleConfirmRefund(row) {
  try {
    await ElMessageBox.confirm(`确认已向用户 ${row.username} 退款 ¥${Number(row.refundAmount).toFixed(2)} 吗？`, '确认退款', { type: 'warning' })
  } catch { return }
  ElMessage.success('退款已确认（请线下操作）')
  await loadList()
}

function viewDetail(row) {
  currentRow.value = row
  detailDialogVisible.value = true
}

onMounted(loadList)
</script>

<style scoped>
.admin-layout { display: flex; min-height: 100vh; background: #f0f2f5; }
.admin-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.admin-content { flex: 1; padding: 20px; background: #fff; margin: 16px; border-radius: 8px; }
.page-title { margin: 0 0 20px; font-size: 22px; color: #303133; }
.filter-bar { display: flex; align-items: center; }
.amount { color: #f56c6c; font-weight: 600; }
.order-no { font-family: monospace; color: #409eff; }
.text-muted { color: #c0c4cc; font-style: italic; }
</style>
