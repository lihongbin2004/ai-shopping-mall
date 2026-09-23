<template>
  <div class="product-list-page">
    <Navbar />
    <div class="main-content">
      <!-- 顶部 Banner -->
      <div class="list-hero glass reveal">
        <div class="hero-info">
          <h1 class="hero-title gradient-text">发现好物 · 享受生活</h1>
          <p class="hero-sub">基于 AI 算法的智能推荐，让每一次购物都精准高效</p>
        </div>
        <div class="hero-decor">
          <span class="deco-orb orb-a"></span>
          <span class="deco-orb orb-b"></span>
        </div>
      </div>

      <div class="filter-bar glass reveal">
        <div class="filter-row">
          <span class="filter-label">分类：</span>
          <div class="tag-list">
            <el-tag
              v-for="cat in categories"
              :key="cat.id"
              :type="selectedCategory === cat.id ? 'primary' : 'info'"
              effect="plain"
              class="filter-tag"
              @click="selectCategory(cat.id)"
            >{{ cat.name }}</el-tag>
            <el-tag :type="!selectedCategory ? 'primary' : 'info'" effect="plain" class="filter-tag" @click="selectCategory(null)">全部</el-tag>
          </div>
        </div>
        <div class="filter-row">
          <span class="filter-label">排序：</span>
          <el-radio-group v-model="sortBy" @change="fetchProducts" class="sort-group">
            <el-radio-button value="">默认</el-radio-button>
            <el-radio-button value="price_asc">价格 ↑</el-radio-button>
            <el-radio-button value="price_desc">价格 ↓</el-radio-button>
            <el-radio-button value="sales">销量</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <div v-if="products.length" class="product-grid">
        <el-row :gutter="20" class="reveal">
          <el-col v-for="(item, idx) in products" :key="item.id" :xs="12" :sm="8" :md="6" :lg="6" :style="{ animationDelay: (idx % 4) * 0.08 + 's' }" class="grid-item">
            <ProductCard :product="item" />
          </el-col>
        </el-row>
        <div class="pagination-wrap reveal">
          <el-pagination
            v-model:current-page="page"
            :page-size="pageSize"
            :total="total"
            layout="prev, pager, next, jumper, total"
            background
            @current-change="fetchProducts"
          />
        </div>
      </div>
      <div v-else class="empty-state reveal-scale">
        <el-empty description="暂无商品，换个分类试试吧" :image-size="120" />
      </div>
    </div>
    <Footer />
    <AiChatWidget />
  </div>
</template>

<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Navbar from '@/components/Navbar.vue'
import Footer from '@/components/Footer.vue'
import ProductCard from '@/components/ProductCard.vue'
import AiChatWidget from '@/components/AiChatWidget.vue'
import { useReveal } from '@/composables/useReveal'
import { getProductList } from '@/api/product'
import { getCategoryList } from '@/api/category'

const route = useRoute()
const router = useRouter()
const products = ref([])
const categories = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const selectedCategory = ref(null)
const keyword = ref('')
const sortBy = ref('')
const loading = ref(false)

const { refresh: refreshReveal } = useReveal(null)

function selectCategory(id) {
  selectedCategory.value = id
  page.value = 1
  // 同步 URL：清空 keyword，更新 categoryId
  router.replace({ path: '/products', query: { categoryId: id || undefined } })
  fetchProducts()
}

async function fetchProducts() {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize.value }
    if (selectedCategory.value) {
      params.categoryId = selectedCategory.value
    }
    if (sortBy.value) {
      params.sortBy = sortBy.value
    }
    if (keyword.value) {
      params.keyword = keyword.value
    }
    const res = await getProductList(params)
    products.value = res.data?.list || res.data?.records || res.data || []
    total.value = res.data?.total || 0
    // 异步渲染出网格后，重新触发 reveal 动画
    nextTick(() => refreshReveal())
  } catch {
    products.value = []
  } finally {
    loading.value = false
  }
}

// 同步 URL 参数到本地状态，并刷新商品列表
function syncFromQuery() {
  const k = route.query.keyword || ''
  const c = route.query.categoryId ? Number(route.query.categoryId) : null
  const keywordChanged = keyword.value !== k
  const categoryChanged = selectedCategory.value !== c
  keyword.value = k
  selectedCategory.value = c
  if (keywordChanged || categoryChanged) {
    page.value = 1
    fetchProducts()
  }
}

watch(() => route.query, syncFromQuery, { deep: true })

onMounted(async () => {
  const catRes = await getCategoryList().catch(() => ({ data: [] }))
  categories.value = catRes.data || []
  syncFromQuery()
  if (!keyword.value && !selectedCategory.value) {
    fetchProducts()
  }
})
</script>

<style scoped>
.product-list-page {
  min-height: 100vh;
  background:
    radial-gradient(ellipse 800px 400px at 20% 0%, rgba(99, 102, 241, 0.06), transparent 60%),
    radial-gradient(ellipse 600px 600px at 100% 50%, rgba(244, 63, 94, 0.04), transparent 60%),
    linear-gradient(180deg, #f8fafc 0%, #ffffff 100%);
}
.main-content { max-width: 1240px; margin: 0 auto; padding: 24px 20px 40px; }

/* ===== 顶部 Hero ===== */
.list-hero {
  position: relative;
  padding: 36px 40px;
  border-radius: 20px;
  margin-bottom: 20px;
  overflow: hidden;
  background:
    linear-gradient(135deg, rgba(99, 102, 241, 0.08), rgba(244, 63, 94, 0.05)),
    linear-gradient(180deg, rgba(255,255,255,0.85), rgba(255,255,255,0.6));
  border: 1px solid rgba(99, 102, 241, 0.15);
  box-shadow: 0 12px 40px rgba(99, 102, 241, 0.08);
}
.list-hero .hero-title {
  font-size: 32px;
  font-weight: 800;
  margin: 0 0 8px;
  letter-spacing: -0.5px;
}
.list-hero .hero-sub {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}
.list-hero .hero-decor {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.deco-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(50px);
  opacity: 0.4;
}
.orb-a {
  width: 160px; height: 160px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  top: -40px; right: 30%;
  animation: float 6s ease-in-out infinite;
}
.orb-b {
  width: 120px; height: 120px;
  background: linear-gradient(135deg, #f43f5e, #fb923c);
  bottom: -30px; right: 8%;
  animation: float 8s ease-in-out infinite 1s;
}

/* ===== 筛选栏 ===== */
.filter-bar {
  padding: 18px 22px;
  border-radius: 16px;
  margin-bottom: 24px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(99, 102, 241, 0.1);
  box-shadow: 0 6px 24px rgba(99, 102, 241, 0.06);
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.filter-row:last-child { margin-bottom: 0; }
.filter-label {
  font-size: 14px;
  color: #475569;
  font-weight: 600;
  white-space: nowrap;
}
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.filter-tag {
  cursor: pointer;
  user-select: none;
  transition: all 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
  font-weight: 500;
}
.filter-tag:hover {
  transform: translateY(-2px) scale(1.05);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.15);
}
.sort-group :deep(.el-radio-button__inner) {
  border-radius: 8px !important;
  font-weight: 500;
  transition: all 0.2s;
}

/* ===== 商品网格 ===== */
.product-grid { margin-top: 8px; }
.grid-item {
  opacity: 0;
  animation: slide-up 0.6s ease-out forwards;
}
.empty-state {
  padding: 60px 0;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 16px;
  backdrop-filter: blur(10px);
}

/* ===== 分页 ===== */
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 36px;
  padding: 16px;
}
.pagination-wrap :deep(.el-pagination) {
  --el-pagination-button-bg-color: rgba(255, 255, 255, 0.9);
  --el-pagination-hover-color: #6366f1;
}
.pagination-wrap :deep(.btn-prev),
.pagination-wrap :deep(.btn-next),
.pagination-wrap :deep(.el-pager li) {
  background: rgba(255, 255, 255, 0.9) !important;
  border-radius: 8px !important;
  margin: 0 3px;
  font-weight: 500;
  transition: all 0.25s;
}
.pagination-wrap :deep(.el-pager li.is-active) {
  background: var(--gradient-primary) !important;
  color: #fff !important;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

@media (max-width: 768px) {
  .list-hero { padding: 24px 20px; }
  .list-hero .hero-title { font-size: 24px; }
  .filter-bar { padding: 14px 16px; }
}
</style>