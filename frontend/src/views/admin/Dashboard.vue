<template>
  <div class="admin-layout">
    <AdminSidebar />
    <div class="admin-main">
      <AdminHeader title="控制台" />
      <div class="admin-content" v-loading="loading">
        <!-- 统计卡片 -->
        <el-row :gutter="16" class="stats-row">
          <el-col :span="6" v-for="stat in stats" :key="stat.label">
            <div class="stat-card" :style="{ borderLeftColor: stat.color }">
              <div class="stat-value">{{ stat.value }}</div>
              <div class="stat-label">{{ stat.label }}</div>
              <el-icon :size="48" class="stat-icon" :color="stat.color">
                <component :is="stat.icon" />
              </el-icon>
            </div>
          </el-col>
        </el-row>

        <!-- 图表 -->
        <el-row :gutter="16" class="chart-row">
          <el-col :span="14">
            <el-card>
              <template #header>销售额趋势</template>
              <div ref="salesChartRef" style="height: 320px"></div>
            </el-card>
          </el-col>
          <el-col :span="10">
            <el-card>
              <template #header>商品分类占比</template>
              <div ref="categoryChartRef" style="height: 320px"></div>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="16" class="chart-row">
          <el-col :span="24">
            <el-card>
              <template #header>商品销量排行</template>
              <div ref="productChartRef" style="height: 320px"></div>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import AdminSidebar from './components/AdminSidebar.vue'
import AdminHeader from './components/AdminHeader.vue'
import { getDashboardStats, getSalesReport, getProductStats, getCategoryStats } from '@/api/admin'

const loading = ref(false)
const salesChartRef = ref(null)
const categoryChartRef = ref(null)
const productChartRef = ref(null)

const stats = ref([
  { label: '用户总数', value: 0, icon: 'User', color: '#409eff' },
  { label: '商品总数', value: 0, icon: 'Goods', color: '#67c23a' },
  { label: '订单总数', value: 0, icon: 'List', color: '#e6a23c' },
  { label: '总收入', value: '¥0', icon: 'Coin', color: '#f56c6c' }
])

function initSalesChart(data) {
  nextTick(() => {
    if (!salesChartRef.value) return
    const chart = echarts.init(salesChartRef.value)
    const dates = data?.map(d => d.date) || ['周一','周二','周三','周四','周五','周六','周日']
    const values = data?.map(d => d.amount) || [120,200,150,80,70,110,130]
    chart.setOption({
      xAxis: { type: 'category', data: dates },
      yAxis: { type: 'value' },
      tooltip: { trigger: 'axis' },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      series: [{
        type: 'line',
        smooth: true,
        data: values,
        areaStyle: { color: 'rgba(64,158,255,0.1)' },
        lineStyle: { color: '#409eff', width: 2 },
        itemStyle: { color: '#409eff' }
      }]
    })
  })
}

function initCategoryChart(data) {
  nextTick(() => {
    if (!categoryChartRef.value) return
    const chart = echarts.init(categoryChartRef.value)
    const pieData = data?.length ? data : [
      { name: '电子产品', value: 35 },
      { name: '服装', value: 25 },
      { name: '食品', value: 20 },
      { name: '家居', value: 12 },
      { name: '其他', value: 8 }
    ]
    chart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c}%' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['50%', '50%'],
        data: pieData,
        label: { formatter: '{b}\n{d}%' },
        emphasis: {
          label: { show: true, fontSize: 14, fontWeight: 'bold' }
        }
      }]
    })
  })
}

function initProductChart(data) {
  nextTick(() => {
    if (!productChartRef.value) return
    const chart = echarts.init(productChartRef.value)
    const names = data?.map(d => d.name) || ['商品A','商品B','商品C','商品D','商品E']
    const values = data?.map(d => d.sales) || [120,200,150,80,70]
    chart.setOption({
      xAxis: { type: 'category', data: names },
      yAxis: { type: 'value' },
      tooltip: { trigger: 'axis' },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      series: [{
        type: 'bar',
        data: values,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0,0,0,1, [
            { offset: 0, color: '#409eff' },
            { offset: 1, color: '#6366f1' }
          ])
        }
      }]
    })
  })
}

onMounted(async () => {
  loading.value = true
  try {
    const [dashRes, salesRes, prodRes, catRes] = await Promise.all([
      getDashboardStats().catch(() => ({ data: {} })),
      getSalesReport({ days: 7 }).catch(() => ({ data: [] })),
      getProductStats().catch(() => ({ data: [] })),
      getCategoryStats().catch(() => ({ data: [] }))
    ])
    const d = dashRes.data || {}
    stats.value = [
      { label: '用户总数', value: d.userCount || 0, icon: 'User', color: '#409eff' },
      { label: '商品总数', value: d.productCount || 0, icon: 'Goods', color: '#67c23a' },
      { label: '订单总数', value: d.orderCount || 0, icon: 'List', color: '#e6a23c' },
      { label: '总收入', value: `¥${(d.totalRevenue || 0).toLocaleString()}`, icon: 'Coin', color: '#f56c6c' }
    ]
    initSalesChart(salesRes.data)
    initCategoryChart(catRes.data)
    initProductChart(prodRes.data)
  } catch {} finally {
    loading.value = false
  }
})
</script>

<style scoped>
.admin-layout { display: flex; min-height: 100vh; background: #f0f2f5; }
.admin-main { flex: 1; display: flex; flex-direction: column; }
.admin-content { padding: 20px; flex: 1; overflow-y: auto; }
.stats-row { margin-bottom: 16px; }
.stat-card { background: #fff; border-radius: 8px; padding: 20px; border-left: 4px solid; position: relative; overflow: hidden; }
.stat-value { font-size: 28px; font-weight: bold; color: #303133; }
.stat-label { font-size: 14px; color: #909399; margin-top: 4px; }
.stat-icon { position: absolute; right: 16px; top: 50%; transform: translateY(-50%); opacity: 0.15; }
.chart-row { margin-bottom: 16px; }
</style>