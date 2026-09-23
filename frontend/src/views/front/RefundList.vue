<template>
  <div class="refund-page">
    <Navbar />
    <div class="refund-container">
      <h2 class="page-title">我的退款/售后</h2>

      <el-tabs v-model="activeTab" @tab-change="loadList">
        <el-tab-pane label="全部" name="all"></el-tab-pane>
        <el-tab-pane label="待审核" name="0"></el-tab-pane>
        <el-tab-pane label="已通过" name="1"></el-tab-pane>
        <el-tab-pane label="已拒绝" name="2"></el-tab-pane>
        <el-tab-pane label="已退款" name="3"></el-tab-pane>
      </el-tabs>

      <div v-if="filteredList.length === 0" class="empty">
        <el-empty description="暂无退款记录" />
      </div>

      <div v-else class="refund-list">
        <div v-for="item in filteredList" :key="item.id" class="refund-card">
          <div class="refund-header">
            <div class="refund-no">退款单号: {{ item.refundNo }}</div>
            <el-tag :type="statusTagType(item.status)" effect="dark">
              {{ statusText(item.status) }}
            </el-tag>
          </div>
          <div class="refund-body">
            <div class="refund-info">
              <div class="info-row">
                <span class="info-label">原订单号:</span>
                <span class="info-value">{{ item.orderNo }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">退款金额:</span>
                <span class="info-value amount">¥{{ Number(item.refundAmount).toFixed(2) }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">退款原因:</span>
                <span class="info-value">{{ item.reason }}</span>
              </div>
              <div v-if="item.description" class="info-row">
                <span class="info-label">详细说明:</span>
                <span class="info-value">{{ item.description }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">申请时间:</span>
                <span class="info-value">{{ formatTime(item.createTime) }}</span>
              </div>
              <div v-if="item.auditTime" class="info-row">
                <span class="info-label">审核时间:</span>
                <span class="info-value">{{ formatTime(item.auditTime) }}</span>
              </div>
              <div v-if="item.rejectReason" class="info-row">
                <span class="info-label">拒绝原因:</span>
                <span class="info-value reject">{{ item.rejectReason }}</span>
              </div>
              <div v-if="item.adminReply" class="info-row">
                <span class="info-label">客服回复:</span>
                <span class="info-value reply">{{ item.adminReply }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <Footer />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import Navbar from '@/components/Navbar.vue'
import Footer from '@/components/Footer.vue'
import { getMyRefunds } from '@/api/refund'

const activeTab = ref('all')
const refundList = ref([])

const filteredList = computed(() => {
  if (activeTab.value === 'all') return refundList.value
  return refundList.value.filter(item => String(item.status) === activeTab.value)
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
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

async function loadList() {
  try {
    const res = await getMyRefunds()
    refundList.value = res.data || []
  } catch (e) {
    console.error('加载退款列表失败', e)
  }
}

onMounted(loadList)
</script>

<style scoped>
.refund-page { min-height: 100vh; background: #f5f7fa; }
.refund-container { max-width: 900px; margin: 20px auto; padding: 24px; background: #fff; border-radius: 12px; box-shadow: 0 2px 12px rgba(0,0,0,0.05); }
.page-title { margin: 0 0 20px; font-size: 22px; color: #303133; }
.empty { padding: 40px 0; }
.refund-list { display: flex; flex-direction: column; gap: 16px; }
.refund-card { border: 1px solid #ebeef5; border-radius: 8px; padding: 18px; background: #fafbfc; transition: all 0.2s; }
.refund-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.08); }
.refund-header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 12px; border-bottom: 1px dashed #ebeef5; margin-bottom: 12px; }
.refund-no { font-size: 14px; color: #606266; font-weight: 500; }
.refund-body { padding: 4px 0; }
.info-row { display: flex; padding: 6px 0; font-size: 14px; }
.info-label { width: 100px; color: #909399; flex-shrink: 0; }
.info-value { color: #303133; flex: 1; word-break: break-word; }
.info-value.amount { color: #f56c6c; font-weight: 600; font-size: 16px; }
.info-value.reject { color: #f56c6c; }
.info-value.reply { color: #67c23a; }
</style>
