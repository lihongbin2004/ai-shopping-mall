<template>
  <div class="detail-page">
    <Navbar />
    <div class="main-content" v-loading="loading">
      <div class="breadcrumb">
        <el-breadcrumb>
          <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item :to="{ path: '/products' }">商品列表</el-breadcrumb-item>
          <el-breadcrumb-item>{{ product.name }}</el-breadcrumb-item>
        </el-breadcrumb>
      </div>

      <div class="product-detail" v-if="product.id">
        <div class="detail-left reveal-left">
            <div class="image-frame">
              <el-image
                :src="currentImage"
                fit="contain"
                class="main-image"
                :preview-src-list="[currentImage]"
            >
              <template #error>
                <div class="image-placeholder">
                  <el-icon :size="64"><Picture /></el-icon>
                  <p>暂无图片</p>
                </div>
              </template>
            </el-image>
            <div class="image-decor">
              <span class="deco-dot d1"></span>
              <span class="deco-dot d2"></span>
              <span class="deco-dot d3"></span>
            </div>
          </div>
          <div class="image-toolbar">
            <span class="toolbar-tag"><el-icon><View /></el-icon> 点击查看大图</span>
            <span class="toolbar-tag"><el-icon><PictureFilled /></el-icon> 高清细节</span>
          </div>
        </div>
        <div class="detail-right reveal-right">
          <div class="badge-row">
            <span v-if="product.sales >= 1000" class="badge badge-hot">🔥 爆款</span>
            <span v-else-if="product.sales >= 100" class="badge badge-hot">🔥 热卖</span>
            <span v-if="product.discount" class="badge badge-discount">限时折扣</span>
            <span class="badge badge-recommend">✓ 正品</span>
          </div>
          <h1 class="product-name">{{ product.name }}</h1>
          <p class="product-desc">{{ product.description }}</p>

          <div class="price-box glass">
            <div class="price-main">
              <span class="current-price">
                <span class="currency">¥</span>{{ currentPrice.toFixed(2) }}
              </span>
              <span v-if="product.originalPrice" class="original-price">¥{{ Number(product.originalPrice).toFixed(2) }}</span>
              <span v-if="product.discount" class="discount-tag">-{{ product.discount }}%</span>
            </div>
            <div class="price-tip">
              <el-icon><InfoFilled /></el-icon>
              <span>价格已含税费，购满 99 元包邮</span>
            </div>
          </div>

          <div class="info-row glass-soft">
            <div class="info-item">
              <el-icon><SoldOut /></el-icon>
              <span>销量 <strong>{{ product.sales || 0 }}</strong></span>
            </div>
            <div class="info-item">
              <el-icon><Box /></el-icon>
              <span>库存 <strong>{{ product.stock || 0 }}</strong></span>
            </div>
            <div class="info-item">
              <el-icon><Star /></el-icon>
              <span>评分 <strong>{{ product.rating || 5.0 }}</strong></span>
            </div>
          </div>

          <!-- 规格选择区 -->
          <div class="spec-section glass-soft" v-if="hasMultiSkus">
            <div v-for="key in product.specKeys" :key="key" class="spec-row">
              <span class="spec-label">{{ key }}：</span>
              <div class="spec-options">
                <span
                  v-for="val in product.specOptions[key]"
                  :key="val"
                  class="spec-tag"
                  :class="{ active: selectedSpecs[key] === val, disabled: !canSelectSpec(key, val) }"
                  @click="selectSpec(key, val)"
                >{{ val }}</span>
              </div>
            </div>
          </div>

          <div class="price-change-hint" v-if="hasMultiSkus && selectedSku" :key="selectedSku.id">
            <span class="hint-line">当前：{{ selectedSku.specDesc }}</span>
          </div>

          <div class="quantity-row">
            <span class="label">数量：</span>
            <el-input-number v-model="quantity" :min="1" :max="currentStock" size="default" />
            <span class="stock-hint">库存 {{ currentStock }} 件</span>
          </div>

          <div class="action-row">
            <el-button type="primary" size="large" :disabled="currentStock <= 0 || (hasMultiSkus && !selectedSku)" @click="handleAddCart" class="ripple action-cart">
              <el-icon><ShoppingCart /></el-icon> 加入购物车
            </el-button>
            <el-button type="danger" size="large" :disabled="currentStock <= 0 || (hasMultiSkus && !selectedSku)" @click="handleBuyNow" class="ripple action-buy">
              <el-icon><Lightning /></el-icon> 立即购买
            </el-button>
            <el-button
              :type="isFav ? 'warning' : 'default'"
              size="large"
              @click="toggleFavorite"
              class="action-fav"
            >
              <el-icon><StarFilled v-if="isFav" /><Star v-else /></el-icon>
              {{ isFav ? '已收藏' : '收藏' }}
            </el-button>
            <el-button size="large" @click="openAiChat" class="action-ai">
              <el-icon><MagicStick /></el-icon> AI 问答
            </el-button>
          </div>

          <!-- 信任徽章 -->
          <div class="trust-row">
            <span class="trust-item"><el-icon><Check /></el-icon> 7天无理由</span>
            <span class="trust-item"><el-icon><Check /></el-icon> 满99包邮</span>
            <span class="trust-item"><el-icon><Check /></el-icon> 极速发货</span>
            <span class="trust-item"><el-icon><Check /></el-icon> 正品保障</span>
          </div>
        </div>
      </div>

      <!-- 评价区 -->
      <div class="reviews-section reveal" v-if="product.id">
        <div class="reviews-header">
          <h3>商品评价 <span class="count">({{ totalReviews }})</span></h3>
          <div class="rating-summary">
            <div class="rating-score">
              <span class="big">★</span>
              <span class="num">{{ product.rating || 5.0 }}</span>
            </div>
            <div class="rating-desc">综合评分</div>
          </div>
        </div>

        <el-card v-if="userStore.isLoggedIn && (canReview || isEditing)" class="review-form" shadow="never">
          <div class="form-title">{{ isEditing ? '修改评价' : '发表评价' }}</div>
          <el-form :model="newReview" label-width="60px">
            <el-form-item label="评分">
              <el-rate v-model="newReview.rating" :max="5" show-text :texts="['非常差', '差', '一般', '满意', '非常满意']" />
            </el-form-item>
            <el-form-item label="内容">
              <el-input
                v-model="newReview.content"
                type="textarea"
                :rows="3"
                maxlength="500"
                show-word-limit
                placeholder="请输入您对商品的评价"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submitReview">提交评价</el-button>
              <el-button v-if="isEditing" @click="resetForm">取消</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-alert v-else-if="userStore.isLoggedIn && !canReview && !isEditing" type="warning" :closable="false" show-icon class="login-tip">
          <template #title>您还没有完成该商品的订单，完成订单后即可发表评价</template>
          <el-button type="warning" size="small" link @click="$router.push('/home')">去逛逛</el-button>
        </el-alert>
        <el-alert v-else type="info" :closable="false" show-icon class="login-tip">
          <template #title>登录后即可发表评价</template>
          <el-button type="primary" size="small" @click="$router.push('/login')">去登录</el-button>
        </el-alert>

        <div v-if="reviews.length" class="review-list">
          <div v-for="rv in reviews" :key="rv.id" class="review-item">
            <div class="review-header">
              <el-avatar :size="32">{{ rv.username?.charAt(0) }}</el-avatar>
              <span class="review-user">{{ rv.username || '匿名用户' }}</span>
              <el-rate :model-value="rv.rating" disabled :max="5" size="small" />
              <span class="review-time">{{ formatTime(rv.createTime) }}</span>
            </div>
            <p class="review-content">{{ rv.content }}</p>
          </div>
          <div class="review-pagination" v-if="totalReviews > pageSize">
            <el-pagination
              v-model:current-page="reviewPage"
              :page-size="pageSize"
              :total="totalReviews"
              layout="prev, pager, next"
              @current-change="loadReviews"
            />
          </div>
        </div>
        <el-empty v-else description="暂无评价，快来抢沙发" :image-size="80" />
      </div>
    </div>
    <Footer />
    <AiChatWidget />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Picture, ShoppingCart, MagicStick, Star, StarFilled, View, PictureFilled, InfoFilled, SoldOut, Box, Check, Lightning } from '@element-plus/icons-vue'
import Navbar from '@/components/Navbar.vue'
import Footer from '@/components/Footer.vue'
import AiChatWidget from '@/components/AiChatWidget.vue'
import { useReveal } from '@/composables/useReveal'
import { getProductDetail } from '@/api/product'
import { addToCart } from '@/api/cart'
import { addFavorite, removeFavorite, checkFavorite } from '@/api/favorite'
import { getProductReviews, addReview, updateReview, getMyReviewForProduct, checkCanReviewProduct } from '@/api/review'
import { getToken } from '@/utils/auth'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()
const userStore = useUserStore()

const { refresh: refreshReveal } = useReveal(null)
const product = ref({})
const loading = ref(false)
const quantity = ref(1)
const isFav = ref(false)
const reviews = ref([])
const totalReviews = ref(0)
const reviewPage = ref(1)
const pageSize = 5
const submitting = ref(false)
const newReview = reactive({ rating: 5, content: '' })
const myReview = ref(null)
const isEditing = ref(false)
const canReview = ref(false)  // 当前登录用户是否可以评价该商品
const canSubmit = computed(() => newReview.rating > 0 && newReview.content.trim().length > 0)

// ========== SKU 规格选择 ==========
const selectedSpecs = reactive({})  // { "颜色": "深空灰", "容量": "16G+512G" }
const selectedSku = ref(null)

/** 是否多规格商品 */
const hasMultiSkus = computed(() => {
  const skus = product.value.skus || []
  return skus.length > 1 || (product.value.specKeys && product.value.specKeys.length >= 1 && skus.length > 0)
})

/** 当前价格（SKU 优先） */
const currentPrice = computed(() => {
  if (selectedSku.value) return Number(selectedSku.value.price)
  return Number(product.value.price || 0)
})

/** 当前库存（SKU 优先） */
const currentStock = computed(() => {
  if (selectedSku.value) return selectedSku.value.stock
  return product.value.stock || 0
})

/** 当前图片（SKU 优先） */
const currentImage = computed(() => {
  if (selectedSku.value && selectedSku.value.image) return selectedSku.value.image
  return product.value.image || ''
})

/** 根据 selectedSpecs 匹配 SKU */
function matchSku() {
  const skus = product.value.skus || []
  if (skus.length === 0) { selectedSku.value = null; return }
  // 所有规格都选了才匹配
  const keys = product.value.specKeys || []
  const allSelected = keys.every(k => selectedSpecs[k])
  if (!allSelected) { selectedSku.value = null; return }
  selectedSku.value = skus.find(sku => {
    if (!sku.specMap) return false
    return keys.every(k => sku.specMap[k] === selectedSpecs[k])
  }) || null
  // 同步数量上限
  if (selectedSku.value && quantity.value > selectedSku.value.stock) {
    quantity.value = Math.min(quantity.value, selectedSku.value.stock || 1)
  }
}

/** 是否可以选这个规格值（被选后要能匹配到有库存的 SKU） */
function canSelectSpec(key, val) {
  const skus = product.value.skus || []
  const keys = product.value.specKeys || []
  // 假设选中这个值后，看剩下规格能不能匹配到至少一个 SKU
  const testSpecs = { ...selectedSpecs, [key]: val }
  const matched = skus.filter(sku => {
    if (!sku.specMap) return false
    return keys.every(k => testSpecs[k] ? sku.specMap[k] === testSpecs[k] : true)
  })
  return matched.some(s => s.stock > 0)
}

function selectSpec(key, val) {
  if (!canSelectSpec(key, val)) return
  // 切换相同值 → 取消选中
  if (selectedSpecs[key] === val) {
    selectedSpecs[key] = null
  } else {
    selectedSpecs[key] = val
  }
  matchSku()
}

/** 加载商品后自动选第一个有库存的 SKU 的规格 */
function initSelectedSpecs() {
  const skus = product.value.skus || []
  const keys = product.value.specKeys || []
  // 清空
  for (const k of keys) selectedSpecs[k] = null
  if (skus.length === 0 || keys.length === 0) { selectedSku.value = null; return }
  // 选第一个有库存的 SKU
  const firstAvailable = skus.find(s => s.stock > 0) || skus[0]
  if (firstAvailable.specMap) {
    for (const k of keys) {
      selectedSpecs[k] = firstAvailable.specMap[k]
    }
  }
  matchSku()
}

async function loadProduct() {
  loading.value = true
  try {
    const res = await getProductDetail(route.params.id)
    product.value = res.data || {}
    initSelectedSpecs()
    await loadReviews()
    if (getToken()) {
      const favRes = await checkFavorite(route.params.id).catch(() => ({ data: false }))
      isFav.value = !!favRes.data
      await loadMyReview()
      await loadCanReview()
    } else {
      canReview.value = false
    }
    nextTick(() => refreshReveal())
  } catch {
    ElMessage.error('商品加载失败')
  } finally {
    loading.value = false
  }
}

async function loadCanReview() {
  try {
    const res = await checkCanReviewProduct(route.params.id)
    canReview.value = !!res.data
  } catch {
    canReview.value = false
  }
}

async function loadMyReview() {
  try {
    const res = await getMyReviewForProduct(route.params.id)
    if (res.data && res.data.id) {
      myReview.value = res.data
      isEditing.value = true
      newReview.rating = res.data.rating
      newReview.content = res.data.content
    } else {
      myReview.value = null
      isEditing.value = false
    }
  } catch {
    myReview.value = null
    isEditing.value = false
  }
}

function resetForm() {
  isEditing.value = false
  newReview.rating = 5
  newReview.content = ''
}

async function submitReview() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  if (!canSubmit.value) {
    ElMessage.warning('请填写评分和内容')
    return
  }
  submitting.value = true
  try {
    if (isEditing.value && myReview.value) {
      await updateReview(myReview.value.id, {
        rating: newReview.rating,
        content: newReview.content.trim()
      })
      ElMessage.success('评价已更新')
    } else {
      await addReview({
        productId: Number(route.params.id),
        rating: newReview.rating,
        content: newReview.content.trim()
      })
      ElMessage.success('评价成功')
    }
    reviewPage.value = 1
    await loadReviews()
    await loadMyReview()
  } catch (e) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

async function loadReviews() {
  try {
    const revRes = await getProductReviews(route.params.id, { page: reviewPage.value, size: pageSize }).catch(() => ({ data: { list: [], total: 0 } }))
    reviews.value = revRes.data?.list || revRes.data?.records || []
    totalReviews.value = revRes.data?.total || 0
  } catch {
    reviews.value = []
    totalReviews.value = 0
  }
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  if (isNaN(d.getTime())) return t
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function handleAddCart() {
  if (!getToken()) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  try {
    await cartStore.addItem({ productId: product.value.id, skuId: selectedSku.value?.id, quantity: quantity.value })
  } catch {}
}

async function handleBuyNow() {
  if (!getToken()) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  try {
    await cartStore.addItem({ productId: product.value.id, skuId: selectedSku.value?.id, quantity: quantity.value })
    router.push('/cart')
  } catch {}
}

async function toggleFavorite() {
  if (!getToken()) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  try {
    if (isFav.value) {
      await removeFavorite(product.value.id)
      isFav.value = false
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(product.value.id)
      isFav.value = true
      ElMessage.success('已收藏')
    }
  } catch {}
}

function openAiChat() {
  router.push('/ai-chat')
}

onMounted(loadProduct)
</script>

<style scoped>
.detail-page {
  min-height: 100vh;
  background:
    radial-gradient(ellipse 800px 400px at 0% 0%, rgba(99, 102, 241, 0.06), transparent 60%),
    radial-gradient(ellipse 600px 600px at 100% 30%, rgba(244, 63, 94, 0.04), transparent 60%),
    linear-gradient(180deg, #f8fafc 0%, #ffffff 100%);
}
.main-content { max-width: 1240px; margin: 0 auto; padding: 24px 20px 40px; }

.breadcrumb { margin-bottom: 16px; }
.breadcrumb :deep(.el-breadcrumb__inner) {
  font-weight: 500;
  transition: color 0.2s;
}
.breadcrumb :deep(.el-breadcrumb__inner:hover) { color: var(--primary); }

/* ============== 详情卡片 ============== */
.product-detail {
  display: flex;
  gap: 40px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  padding: 32px;
  margin-bottom: 24px;
  box-shadow: 0 12px 40px rgba(99, 102, 241, 0.08);
  border: 1px solid rgba(99, 102, 241, 0.08);
}

/* 左侧图片区 */
.detail-left { width: 440px; flex-shrink: 0; }
.image-frame {
  position: relative;
  width: 440px;
  height: 440px;
  border-radius: 16px;
  background:
    linear-gradient(135deg, #f8fafc, #f1f5f9),
    radial-gradient(circle at 30% 30%, rgba(99, 102, 241, 0.08), transparent 60%);
  border: 1px solid #f1f5f9;
  overflow: hidden;
  transition: all 0.4s;
}
.image-frame:hover {
  box-shadow: 0 16px 48px rgba(99, 102, 241, 0.18);
  transform: translateY(-2px);
}
.main-image {
  width: 440px;
  height: 440px;
  background: transparent;
  border-radius: 16px;
  transition: transform 0.5s cubic-bezier(0.16, 1, 0.3, 1);
}
.image-frame:hover .main-image { transform: scale(1.04); }
.image-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 440px;
  color: #cbd5e1;
  gap: 12px;
}
.image-decor {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.deco-dot {
  position: absolute;
  width: 80px; height: 80px;
  border-radius: 50%;
  filter: blur(40px);
  opacity: 0.5;
}
.deco-dot.d1 {
  background: #6366f1;
  top: -20px; left: -20px;
  animation: float 5s ease-in-out infinite;
}
.deco-dot.d2 {
  background: #f43f5e;
  bottom: -20px; right: 20%;
  width: 60px; height: 60px;
  animation: float 7s ease-in-out infinite 1s;
}
.deco-dot.d3 {
  background: #06b6d4;
  top: 30%; right: -20px;
  width: 50px; height: 50px;
  animation: float 6s ease-in-out infinite 0.5s;
}
.image-toolbar {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}
.toolbar-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 12px;
  font-size: 12px;
  color: #64748b;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  border-radius: 999px;
  border: 1px solid #e2e8f0;
  transition: all 0.2s;
}
.toolbar-tag:hover {
  color: var(--primary);
  border-color: rgba(99, 102, 241, 0.3);
}

/* 右侧信息区 */
.detail-right { flex: 1; }
.badge-row {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.product-name {
  font-size: 28px;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 10px;
  line-height: 1.4;
  letter-spacing: -0.3px;
}
.product-desc {
  color: #64748b;
  font-size: 14px;
  margin: 0 0 20px;
  line-height: 1.6;
}

/* 价格区 */
.price-box {
  position: relative;
  padding: 20px 24px;
  border-radius: 14px;
  background:
    linear-gradient(135deg, rgba(254, 242, 242, 0.85), rgba(252, 231, 243, 0.7));
  margin-bottom: 20px;
  border: 1px solid rgba(244, 63, 94, 0.15);
  overflow: hidden;
}
.price-box::before {
  content: '';
  position: absolute;
  top: -40px; right: -40px;
  width: 140px; height: 140px;
  background: radial-gradient(circle, rgba(244, 63, 94, 0.18), transparent 70%);
  filter: blur(30px);
  pointer-events: none;
}
.price-main {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 8px;
  position: relative;
}
.current-price {
  font-size: 36px;
  font-weight: 800;
  background: var(--gradient-warm);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  display: inline-flex;
  align-items: baseline;
  line-height: 1;
}
.current-price .currency {
  font-size: 18px;
  font-weight: 600;
  margin-right: 2px;
}
.original-price {
  font-size: 16px;
  color: #94a3b8;
  text-decoration: line-through;
}
.discount-tag {
  background: var(--gradient-warm);
  color: #fff;
  padding: 3px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(244, 63, 94, 0.3);
}
.price-tip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #64748b;
  position: relative;
}

/* 信息行 */
.info-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 20px;
  border-radius: 12px;
  margin-bottom: 20px;
  background: rgba(241, 245, 249, 0.6);
}
.glass-soft {
  background: rgba(241, 245, 249, 0.6) !important;
  border: 1px solid rgba(99, 102, 241, 0.06);
}
.info-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #475569;
  padding: 0 12px;
  border-right: 1px solid #e2e8f0;
}
.info-item:last-child { border-right: none; }
.info-item .el-icon { color: var(--primary); }
.info-item strong {
  color: #1f2937;
  font-weight: 700;
  margin-left: 2px;
}

/* 规格选择 */
.spec-section {
  padding: 16px 20px;
  border-radius: 12px;
  margin-bottom: 12px;
}
.spec-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 10px;
}
.spec-row:last-child { margin-bottom: 0; }
.spec-label {
  min-width: 48px;
  color: #475569;
  font-size: 14px;
  font-weight: 500;
  padding-top: 6px;
}
.spec-options {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  flex: 1;
}
.spec-tag {
  display: inline-block;
  padding: 6px 16px;
  font-size: 13px;
  color: #475569;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
}
.spec-tag:hover:not(.disabled) {
  color: var(--primary);
  border-color: rgba(99, 102, 241, 0.4);
  background: rgba(99, 102, 241, 0.06);
}
.spec-tag.active {
  color: #fff;
  background: var(--primary);
  border-color: var(--primary);
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.35);
}
.spec-tag.disabled {
  color: #cbd5e1;
  background: #f8fafc;
  border-color: #f1f5f9;
  cursor: not-allowed;
  text-decoration: line-through;
}
.price-change-hint {
  margin-bottom: 12px;
}
.hint-line {
  display: inline-block;
  font-size: 12px;
  color: #6366f1;
  background: rgba(99, 102, 241, 0.08);
  padding: 4px 12px;
  border-radius: 4px;
}

/* 数量行 */
.quantity-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}
.label { color: #475569; font-size: 14px; font-weight: 500; }
.stock-hint {
  font-size: 12px;
  color: #94a3b8;
  margin-left: 4px;
}

/* 操作按钮 */
.action-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}
.action-row .el-button {
  font-weight: 600;
  letter-spacing: 0.5px;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.action-cart, .action-buy {
  flex: 1;
  min-width: 160px;
  height: 48px !important;
  font-size: 15px !important;
}
.action-cart:hover, .action-buy:hover {
  transform: translateY(-3px) scale(1.02);
  box-shadow: 0 12px 28px rgba(99, 102, 241, 0.35);
}
.action-buy:hover {
  box-shadow: 0 12px 28px rgba(244, 63, 94, 0.35);
}
.action-fav, .action-ai {
  height: 48px !important;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.action-fav:hover, .action-ai:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
}

/* 信任徽章 */
.trust-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 16px;
  border-top: 1px dashed #e2e8f0;
}
.trust-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #10b981;
  font-weight: 500;
}
.trust-item .el-icon { font-size: 14px; }

/* ============== 评价区 ============== */
.reviews-section {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  padding: 28px;
  box-shadow: 0 8px 32px rgba(99, 102, 241, 0.06);
  border: 1px solid rgba(99, 102, 241, 0.08);
}
.reviews-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 18px;
  margin-bottom: 18px;
  border-bottom: 1px solid #f1f5f9;
}
.reviews-header h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #1f2937;
}
.reviews-header h3 .count {
  font-size: 14px;
  color: #94a3b8;
  font-weight: 500;
  margin-left: 4px;
}
.rating-summary {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}
.rating-score {
  display: inline-flex;
  align-items: baseline;
  gap: 2px;
  color: #f59e0b;
  font-weight: 700;
}
.rating-score .big { font-size: 18px; }
.rating-score .num { font-size: 22px; }
.rating-desc {
  font-size: 12px;
  color: #94a3b8;
}

.review-form {
  margin-bottom: 20px;
  background: linear-gradient(135deg, #f8fafc, #f1f5f9);
  border: 1px solid #e2e8f0 !important;
  border-radius: 12px !important;
}
.form-title { font-weight: 600; color: #1f2937; margin-bottom: 12px; font-size: 15px; }
.login-tip { margin-bottom: 20px; border-radius: 10px !important; }
.review-list { margin-top: 8px; }
.review-item {
  padding: 18px 0;
  border-bottom: 1px solid #f1f5f9;
  transition: background 0.2s;
}
.review-item:hover { background: rgba(99, 102, 241, 0.02); }
.review-item:last-child { border-bottom: none; }
.review-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.review-user { font-weight: 600; color: #1f2937; }
.review-time { font-size: 12px; color: #94a3b8; margin-left: auto; }
.review-content {
  margin: 0;
  color: #475569;
  line-height: 1.7;
  font-size: 14px;
}
.review-pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

@media (max-width: 1024px) {
  .product-detail { flex-direction: column; gap: 24px; }
  .detail-left, .image-frame, .main-image { width: 100%; }
  .image-frame, .main-image { height: 360px; }
  .image-placeholder { height: 360px; }
  .product-name { font-size: 22px; }
  .current-price { font-size: 28px; }
}
</style>