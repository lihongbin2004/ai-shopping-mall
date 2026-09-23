<template>
  <div class="knowledge-page">
    <div class="knowledge-container">
      <header class="page-header">
        <div class="header-left">
          <el-icon :size="28" color="#6366f1"><Reading /></el-icon>
          <div>
            <h1>RAG 知识库</h1>
            <p class="header-sub">{{ doc?.title || '加载中...' }}</p>
          </div>
        </div>
        <div class="header-right">
          <el-button :icon="ChatDotRound" type="primary" @click="$router.push('/ai-chat')">
            返回 AI 助手
          </el-button>
          <el-button :icon="Refresh" @click="load" :loading="loading">刷新</el-button>
        </div>
      </header>

      <p class="intro">{{ doc?.intro }}</p>

      <div class="stat-row" v-if="doc?.stats">
        <div class="stat-card">
          <el-icon :size="22" color="#6366f1"><Files /></el-icon>
          <div>
            <div class="stat-num">{{ doc.stats.categoryCount }}</div>
            <div class="stat-label">商品分类</div>
          </div>
        </div>
        <div class="stat-card">
          <el-icon :size="22" color="#10b981"><Goods /></el-icon>
          <div>
            <div class="stat-num">{{ doc.stats.productCount }}</div>
            <div class="stat-label">商品（实时）</div>
          </div>
        </div>
        <div class="stat-card">
          <el-icon :size="22" color="#f59e0b"><Histogram /></el-icon>
          <div>
            <div class="stat-num">{{ doc.sections?.length || 0 }}</div>
            <div class="stat-label">知识段落</div>
          </div>
        </div>
        <div class="stat-card">
          <el-icon :size="22" color="#ef4444"><Clock /></el-icon>
          <div>
            <div class="stat-num">{{ formatTime(doc.stats.updatedAt) }}</div>
            <div class="stat-label">最近更新</div>
          </div>
        </div>
      </div>

      <div v-if="loading" class="loading">
        <el-icon class="rotating" :size="32"><Loading /></el-icon>
        <p>正在加载知识库...</p>
      </div>

      <div v-else class="sections">
        <section
          v-for="(sec, idx) in doc?.sections || []"
          :key="idx"
          class="section-card"
        >
          <div class="section-head">
            <el-icon :size="18" :color="iconColor(sec.type)">{{ iconOf(sec.type) }}</el-icon>
            <h2>{{ sec.title }}</h2>
          </div>

          <!-- text -->
          <p v-if="sec.type === 'text'" class="section-body">{{ sec.body }}</p>

          <!-- list -->
          <ul v-else-if="sec.type === 'list'" class="section-list">
            <li v-for="(it, i) in sec.items" :key="i">
              <el-icon color="#6366f1"><CircleCheck /></el-icon>
              <span v-html="formatBullet(it)"></span>
            </li>
          </ul>

          <!-- data: category / product -->
          <div v-else-if="sec.type === 'data'" class="data-grid">
            <template v-if="sec.items.length === 0">
              <p class="empty">暂无数据</p>
            </template>
            <template v-else-if="isCategory(sec.items)">
              <div
                v-for="(it, i) in sec.items"
                :key="i"
                class="data-chip"
              >
                <el-icon><Folder /></el-icon>
                <span>{{ it.name }}</span>
              </div>
            </template>
            <template v-else>
              <div
                v-for="(it, i) in sec.items"
                :key="i"
                class="product-card"
              >
                <div class="product-name">
                  <el-icon color="#6366f1"><Box /></el-icon>
                  {{ it.name }}
                </div>
                <div class="product-meta">
                  <span class="price">¥{{ formatPrice(it.price) }}</span>
                  <span class="sales">已售 {{ it.sales }}</span>
                  <span class="stock" v-if="it.stock !== undefined">库存 {{ it.stock }}</span>
                </div>
                <p v-if="it.description" class="product-desc">{{ it.description }}</p>
              </div>
            </template>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import {
  Reading,
  Refresh,
  ChatDotRound,
  Files,
  Goods,
  Histogram,
  Clock,
  Loading,
  Document,
  List,
  CircleCheck,
  Folder,
  Box
} from '@element-plus/icons-vue'
import { getKnowledge } from '@/api/ai'

const doc = ref(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await getKnowledge()
    doc.value = res.data || null
  } catch (e) {
    console.error('加载知识库失败', e)
    doc.value = null
  } finally {
    loading.value = false
  }
}

function iconOf(type) {
  if (type === 'text') return Document
  if (type === 'list') return List
  if (type === 'data') return Goods
  return Document
}
function iconColor(type) {
  if (type === 'text') return '#6366f1'
  if (type === 'list') return '#10b981'
  if (type === 'data') return '#f59e0b'
  return '#6366f1'
}
function isCategory(items) {
  return items.length > 0 && items[0].name !== undefined && items[0].price === undefined
}
function formatBullet(s) {
  if (!s) return ''
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
}
function formatPrice(p) {
  if (p == null) return '0.00'
  return Number(p).toFixed(2)
}
function formatTime(t) {
  if (!t) return '-'
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return '-'
  const pad = n => (n < 10 ? '0' + n : n)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

onMounted(load)
</script>

<style scoped>
.knowledge-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #eef2ff 0%, #f5f3ff 50%, #fdf4ff 100%);
  padding: 24px 16px 60px;
}
.knowledge-container {
  max-width: 1100px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  padding: 20px 24px;
  border-radius: 16px;
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.08);
  margin-bottom: 16px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}
.header-left h1 {
  margin: 0;
  font-size: 22px;
  color: #1e293b;
}
.header-sub {
  margin: 2px 0 0;
  font-size: 13px;
  color: #94a3b8;
}
.header-right {
  display: flex;
  gap: 10px;
}

.intro {
  background: rgba(255, 255, 255, 0.7);
  padding: 16px 20px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.7;
  color: #475569;
  border-left: 4px solid #6366f1;
  margin-bottom: 20px;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 20px;
}
.stat-card {
  background: #fff;
  padding: 16px 18px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
  transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 20px rgba(99, 102, 241, 0.12);
}
.stat-num {
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
  line-height: 1.2;
}
.stat-label {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 2px;
}

.loading {
  text-align: center;
  padding: 60px 0;
  color: #94a3b8;
}
.rotating {
  animation: rotate 1.4s linear infinite;
}
@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.sections {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.section-card {
  background: #fff;
  padding: 22px 24px;
  border-radius: 14px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.25s;
}
.section-card:hover {
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.1);
}
.section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px dashed #e2e8f0;
}
.section-head h2 {
  margin: 0;
  font-size: 16px;
  color: #1e293b;
}

.section-body {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: #475569;
}

.section-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.section-list li {
  display: flex;
  gap: 8px;
  font-size: 14px;
  line-height: 1.7;
  color: #475569;
  align-items: flex-start;
}
.section-list li :deep(strong) {
  color: #6366f1;
  font-weight: 600;
}

.data-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.data-chip {
  background: linear-gradient(135deg, #eef2ff, #f5f3ff);
  border: 1px solid #c7d2fe;
  padding: 10px 14px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #4338ca;
  font-size: 14px;
  font-weight: 500;
}

.product-card {
  background: linear-gradient(135deg, #fffbeb, #fef3c7);
  border: 1px solid #fde68a;
  border-radius: 10px;
  padding: 12px 14px;
  transition: transform 0.2s, box-shadow 0.2s;
}
.product-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(245, 158, 11, 0.15);
}
.product-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 6px;
}
.product-meta {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: #64748b;
  flex-wrap: wrap;
}
.product-meta .price {
  color: #dc2626;
  font-weight: 700;
  font-size: 14px;
}
.product-meta .sales,
.product-meta .stock {
  background: rgba(255, 255, 255, 0.6);
  padding: 1px 6px;
  border-radius: 4px;
}
.product-desc {
  margin: 6px 0 0;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.empty {
  grid-column: 1 / -1;
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
  padding: 20px 0;
  margin: 0;
}

@media (max-width: 768px) {
  .stat-row {
    grid-template-columns: repeat(2, 1fr);
  }
  .page-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }
}
</style>
