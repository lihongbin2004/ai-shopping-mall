<template>
  <div class="home-page">
    <Navbar />

    <!-- ========== 顶部 Hero 横幅区 ========== -->
    <section class="hero-section">
      <div class="hero-bg">
        <div class="hero-orb orb-1"></div>
        <div class="hero-orb orb-2"></div>
        <div class="hero-orb orb-3"></div>
      </div>
      <div class="hero-inner">
        <div class="hero-content">
          <div class="hero-tag animate-slide-up">
            <el-icon><MagicStick /></el-icon>
            <span>AI 驱动的智能商城</span>
          </div>
          <h1 class="hero-title animate-slide-up">
            发现 <span class="gradient-text">好物</span><br />
            让生活更 <span class="gradient-text-warm">精彩</span>
          </h1>
          <p class="hero-subtitle animate-slide-up">
            基于深度学习的智能推荐，为你精选每一件好物
          </p>
          <div class="hero-actions animate-slide-up">
            <el-button type="primary" size="large" round @click="goToProducts">
              <el-icon><ShoppingCart /></el-icon> 立即抢购
            </el-button>
            <el-button size="large" round @click="goToAiChat">
              <el-icon><ChatDotRound /></el-icon> 问问 AI
            </el-button>
          </div>

          <!-- 实时数据 -->
          <div class="hero-stats">
            <div class="stat-item">
              <div class="stat-num">{{ stats.productCount || '...' }}+</div>
              <div class="stat-label">精选商品</div>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <div class="stat-num">{{ stats.userCount || '10' }}+</div>
              <div class="stat-label">万用户</div>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <div class="stat-num">99<span class="stat-unit">%</span></div>
              <div class="stat-label">好评率</div>
            </div>
          </div>
        </div>

        <!-- 浮动商品卡 -->
        <div class="hero-float-cards">
          <div class="float-card card-1 animate-float" v-if="hotProducts[0]">
            <div class="float-card-img" :style="{ backgroundImage: hotProducts[0].image ? `url(${hotProducts[0].image})` : 'linear-gradient(135deg, #f0f9ff, #e0e7ff)' }"></div>
            <div class="float-card-info">
              <div class="float-card-name">{{ hotProducts[0].name }}</div>
              <div class="float-card-price">¥{{ Number(hotProducts[0].price).toFixed(2) }}</div>
            </div>
            <div class="float-card-badge">🔥 热卖</div>
          </div>
          <div class="float-card card-2 animate-float" style="animation-delay: 0.5s" v-if="hotProducts[1]">
            <div class="float-card-img" :style="{ backgroundImage: hotProducts[1].image ? `url(${hotProducts[1].image})` : 'linear-gradient(135deg, #fef3c7, #fce7f3)' }"></div>
            <div class="float-card-info">
              <div class="float-card-name">{{ hotProducts[1].name }}</div>
              <div class="float-card-price">¥{{ Number(hotProducts[1].price).toFixed(2) }}</div>
            </div>
            <div class="float-card-badge" style="background: var(--gradient-cool)">✨ 新品</div>
          </div>
        </div>
      </div>
    </section>

    <!-- ========== 特色优势区 ========== -->
    <section class="features-section reveal">
      <div class="main-content">
        <div class="features-grid stagger-children">
          <div class="feature-item" v-for="(f, i) in features" :key="i" :style="{ animationDelay: i * 0.1 + 's' }">
            <div class="feature-icon" :style="{ background: f.bg }">
              <el-icon :size="28" color="#fff">
                <component :is="f.icon" />
              </el-icon>
            </div>
            <div class="feature-text">
              <div class="feature-title">{{ f.title }}</div>
              <div class="feature-desc">{{ f.desc }}</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ========== 限时秒杀 ========== -->
    <section class="flash-section" v-if="flashSaleProducts.length">
      <div class="main-content">
        <div class="section-header reveal">
          <div class="section-title-group">
            <div class="flash-icon animate-pulse">⚡</div>
            <div>
              <h2 class="section-title">限时秒杀</h2>
              <p class="section-subtitle">每日精选超值好物，限时特价</p>
            </div>
          </div>
          <div class="countdown glass">
            <span class="countdown-label">距结束</span>
            <div class="countdown-time">
              <span class="time-block">{{ countdown.h }}</span>
              <span class="time-sep">:</span>
              <span class="time-block">{{ countdown.m }}</span>
              <span class="time-sep">:</span>
              <span class="time-block">{{ countdown.s }}</span>
            </div>
          </div>
        </div>
        <el-row :gutter="20" class="reveal-scale">
          <el-col v-for="item in flashSaleProducts" :key="item.id" :xs="12" :sm="8" :md="6" :lg="6" :xl="6">
            <ProductCard :product="item" />
          </el-col>
        </el-row>
      </div>
    </section>

    <!-- ========== 商品分类 ========== -->
    <section class="category-section">
      <div class="main-content">
        <div class="section-header reveal">
          <div class="section-title-group">
            <h2 class="section-title">商品分类</h2>
            <p class="section-subtitle">探索你感兴趣的商品品类</p>
          </div>
        </div>
        <div class="category-grid stagger-children">
          <div
            v-for="(cat, i) in categories"
            :key="cat.id"
            class="category-item"
            @click="goToCategory(cat.id)"
            :style="{ animationDelay: i * 0.05 + 's' }"
          >
            <div class="category-icon-wrap" :style="{ background: catBgColor(i) }">
              <span class="category-emoji">{{ catEmoji(cat.name) }}</span>
            </div>
            <div class="category-name">{{ cat.name }}</div>
            <div class="category-arrow">
              <el-icon><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ========== 热销商品 ========== -->
    <section class="products-section">
      <div class="main-content">
        <div class="section-header reveal">
          <div class="section-title-group">
            <h2 class="section-title">
              <span class="title-icon">🔥</span> 热销商品
            </h2>
            <p class="section-subtitle">大家都在买的热门商品</p>
          </div>
          <el-button text class="more-btn" @click="goToProducts">
            查看更多 <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
        <el-row :gutter="20" v-if="hotProducts.length" class="reveal">
          <el-col v-for="item in hotProducts.slice(0, 8)" :key="item.id" :xs="12" :sm="8" :md="6" :lg="6" :xl="6">
            <ProductCard :product="item" />
          </el-col>
        </el-row>
        <el-empty v-else description="暂无热销商品" />
      </div>
    </section>

    <!-- ========== 品牌墙 ========== -->
    <section class="brands-section">
      <div class="main-content">
        <div class="brands-banner glass reveal-scale">
          <div class="brands-text reveal-left">
            <h2 class="gradient-text">品质保障</h2>
            <p>严选全球优质品牌，为你把关每一件商品</p>
            <div class="brands-tags">
              <span class="brand-chip">🏆 正品承诺</span>
              <span class="brand-chip">🚚 闪电发货</span>
              <span class="brand-chip">🛡️ 7天无理由</span>
            </div>
          </div>
          <div class="brands-grid stagger-children">
            <div class="brand-item" v-for="(b, i) in brands" :key="i" :style="{ animationDelay: i * 0.1 + 's' }">
              <span class="brand-icon">{{ b.icon }}</span>
              <span class="brand-name">{{ b.name }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ========== 新品上架 ========== -->
    <section class="products-section alt-bg">
      <div class="main-content">
        <div class="section-header reveal">
          <div class="section-title-group">
            <h2 class="section-title">
              <span class="title-icon">✨</span> 新品上架
            </h2>
            <p class="section-subtitle">最新上架，等你发现</p>
          </div>
          <el-button text class="more-btn" @click="goToProducts">
            查看更多 <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
        <el-row :gutter="20" v-if="newProducts.length" class="reveal">
          <el-col v-for="item in newProducts.slice(0, 8)" :key="item.id" :xs="12" :sm="8" :md="6" :lg="6" :xl="6">
            <ProductCard :product="item" />
          </el-col>
        </el-row>
        <el-empty v-else description="暂无新品" />
      </div>
    </section>

    <!-- ========== 为您推荐 ========== -->
    <section class="products-section">
      <div class="main-content">
        <div class="section-header reveal">
          <div class="section-title-group">
            <h2 class="section-title">
              <span class="title-icon">🎁</span> 为您推荐
            </h2>
            <p class="section-subtitle">基于 AI 算法的个性化推荐</p>
          </div>
          <el-button text class="more-btn" @click="goToProducts">
            查看更多 <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
        <el-row :gutter="20" v-if="recommendProducts.length" class="reveal">
          <el-col v-for="item in recommendProducts.slice(0, 8)" :key="item.id" :xs="12" :sm="8" :md="6" :lg="6" :xl="6">
            <ProductCard :product="item" />
          </el-col>
        </el-row>
        <el-empty v-else description="暂无推荐" />
      </div>
    </section>

    <Footer />
    <AiChatWidget />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import {
  ShoppingCart, ChatDotRound, MagicStick, Van, Promotion, Medal,
  Service, Refresh, Discount, ArrowRight, Present
} from '@element-plus/icons-vue'
import Navbar from '@/components/Navbar.vue'
import Footer from '@/components/Footer.vue'
import ProductCard from '@/components/ProductCard.vue'
import AiChatWidget from '@/components/AiChatWidget.vue'
import { useReveal } from '@/composables/useReveal'
import { getHotProducts, getNewProducts, getProductRecommend } from '@/api/product'
import { getCategoryList } from '@/api/category'

const router = useRouter()

// 触发滚动入场动画
useReveal(null)
const { refresh: refreshReveal } = useReveal(null)

// ========== Banner / 特性 / 品牌 ==========
const features = [
  { icon: Van, title: '极速物流', desc: '次日达 极速送达', bg: 'linear-gradient(135deg, #f43f5e, #fb923c)' },
  { icon: Promotion, title: '超值优惠', desc: '每日特价 限时秒杀', bg: 'linear-gradient(135deg, #6366f1, #8b5cf6)' },
  { icon: Medal, title: '品质保障', desc: '正品保证 假一赔十', bg: 'linear-gradient(135deg, #10b981, #06b6d4)' },
  { icon: Service, title: '贴心服务', desc: '7×24 在线客服', bg: 'linear-gradient(135deg, #f59e0b, #f43f5e)' }
]

const brands = [
  { icon: '📱', name: 'Apple' },
  { icon: '💻', name: '华为' },
  { icon: '👕', name: '优衣库' },
  { icon: '🍵', name: '茶颜悦色' },
  { icon: '📚', name: '中信出版' },
  { icon: '🏠', name: '小米有品' }
]

// 分类对应 emoji
const catEmojis = ['📱', '👕', '🍔', '🏠', '📚', '🎮', '💄', '🏃', '🍼', '🌸', '🎁', '⌚']
function catEmoji(name) {
  // 简单关键字匹配
  const map = {
    '电子': '📱', '手机': '📱', '数码': '💻',
    '服装': '👕', '鞋帽': '👟', '美妆': '💄',
    '食品': '🍔', '零食': '🍿', '茶': '🍵',
    '家居': '🏠', '家电': '🔌', '家具': '🛋️',
    '图书': '📚', '书籍': '📖',
    '运动': '🏃', '户外': '⛺',
    '玩具': '🧸', '游戏': '🎮',
    '宠物': '🐶', '婴儿': '🍼',
    '汽车': '🚗',
    '保健': '💊', '健康': '💪',
    '办公': '🖇️'
  }
  for (const k in map) if (name && name.includes(k)) return map[k]
  return catEmojis[Math.floor(Math.random() * catEmojis.length)]
}

function catBgColor(i) {
  const colors = [
    'linear-gradient(135deg, #fce7f3, #fbcfe8)',
    'linear-gradient(135deg, #dbeafe, #bfdbfe)',
    'linear-gradient(135deg, #d1fae5, #a7f3d0)',
    'linear-gradient(135deg, #fed7aa, #fdba74)',
    'linear-gradient(135deg, #ddd6fe, #c4b5fd)',
    'linear-gradient(135deg, #fef3c7, #fde68a)',
    'linear-gradient(135deg, #ccfbf1, #99f6e4)',
    'linear-gradient(135deg, #fecaca, #fca5a5)'
  ]
  return colors[i % colors.length]
}

const stats = ref({ productCount: 0, userCount: 10 })

// ========== 商品数据 ==========
const categories = ref([])
const hotProducts = ref([])
const newProducts = ref([])
const recommendProducts = ref([])
const flashSaleProducts = ref([])

function goToProducts() {
  router.push('/products')
}
function goToCategory(id) {
  router.push({ path: '/products', query: { categoryId: id } })
}
function goToAiChat() {
  router.push('/ai-chat')
}

// ========== 倒计时（每日 24:00 结束） ==========
const countdown = ref({ h: '00', m: '00', s: '00' })
let timer = null
function updateCountdown() {
  const now = new Date()
  const end = new Date(now)
  end.setHours(24, 0, 0, 0)
  let diff = Math.max(0, end - now)
  const h = Math.floor(diff / 3600000)
  diff %= 3600000
  const m = Math.floor(diff / 60000)
  diff %= 60000
  const s = Math.floor(diff / 1000)
  countdown.value = {
    h: String(h).padStart(2, '0'),
    m: String(m).padStart(2, '0'),
    s: String(s).padStart(2, '0')
  }
}

onMounted(async () => {
  updateCountdown()
  timer = setInterval(updateCountdown, 1000)

  try {
    const [catRes, hotRes, newRes, recRes] = await Promise.all([
      getCategoryList(),
      getHotProducts(),
      getNewProducts(),
      getProductRecommend()
    ])
    categories.value = (catRes.data || []).slice(0, 8)
    hotProducts.value = hotRes.data?.list || []
    newProducts.value = newRes.data?.list || []
    recommendProducts.value = recRes.data?.list || []
    stats.value.productCount = (hotProducts.value.length + newProducts.value.length + recommendProducts.value.length) || 0

    // 从热销里挑 4 个作为秒杀
    flashSaleProducts.value = hotProducts.value.slice(0, 4)
    nextTick(() => refreshReveal())
  } catch (err) {
    console.error('Home load error:', err)
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background:
    radial-gradient(ellipse 800px 400px at 20% 0%, rgba(99, 102, 241, 0.08), transparent 60%),
    radial-gradient(ellipse 600px 600px at 100% 30%, rgba(244, 63, 94, 0.05), transparent 60%),
    linear-gradient(180deg, #f8fafc 0%, #ffffff 30%, #f8fafc 100%);
  overflow-x: hidden;
}

.main-content {
  max-width: 1240px;
  margin: 0 auto;
  padding: 0 20px;
}

/* ============== Hero ============== */
.hero-section {
  position: relative;
  padding: 60px 20px 80px;
  overflow: hidden;
}
.hero-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}
.hero-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;
}
.orb-1 {
  width: 400px; height: 400px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  top: -100px; left: -100px;
  animation: float 6s ease-in-out infinite;
}
.orb-2 {
  width: 350px; height: 350px;
  background: linear-gradient(135deg, #f43f5e, #fb923c);
  top: 50%; right: -50px;
  animation: float 8s ease-in-out infinite 1s;
}
.orb-3 {
  width: 300px; height: 300px;
  background: linear-gradient(135deg, #06b6d4, #3b82f6);
  bottom: -100px; left: 30%;
  animation: float 7s ease-in-out infinite 0.5s;
}
.hero-inner {
  position: relative;
  z-index: 1;
  max-width: 1240px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 40px;
  align-items: center;
}
.hero-content {
  animation: slide-up 0.8s ease-out;
}
.hero-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: rgba(99, 102, 241, 0.1);
  color: #6366f1;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 20px;
  border: 1px solid rgba(99, 102, 241, 0.2);
}
.hero-title {
  font-size: 56px;
  font-weight: 800;
  line-height: 1.15;
  color: #1f2937;
  margin: 0 0 16px;
  letter-spacing: -1px;
}
.hero-subtitle {
  font-size: 18px;
  color: #64748b;
  margin: 0 0 32px;
  line-height: 1.6;
}
.hero-actions {
  display: flex;
  gap: 16px;
  margin-bottom: 48px;
}
.hero-actions .el-button {
  font-weight: 600;
  padding: 14px 28px;
}
.hero-stats {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 24px 32px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 8px 32px rgba(99, 102, 241, 0.08);
  max-width: 480px;
}
.stat-item { flex: 1; text-align: center; }
.stat-num {
  font-size: 28px;
  font-weight: 800;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  line-height: 1;
}
.stat-unit { font-size: 18px; }
.stat-label {
  font-size: 13px;
  color: #94a3b8;
  margin-top: 4px;
}
.stat-divider {
  width: 1px;
  height: 32px;
  background: linear-gradient(180deg, transparent, #e2e8f0, transparent);
}

/* Hero 浮动卡片 */
.hero-float-cards {
  position: relative;
  height: 460px;
}
.float-card {
  position: absolute;
  background: #fff;
  border-radius: 16px;
  padding: 12px;
  box-shadow: 0 20px 40px rgba(99, 102, 241, 0.15);
  width: 220px;
  cursor: pointer;
  transition: transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.4s;
  border: 1px solid rgba(255, 255, 255, 0.6);
  overflow: hidden;
}
.float-card::before {
  content: '';
  position: absolute;
  inset: -2px;
  background: var(--gradient-primary);
  border-radius: 18px;
  opacity: 0;
  z-index: -1;
  transition: opacity 0.3s;
  filter: blur(10px);
}
.float-card:hover {
  transform: translateY(-8px) scale(1.04) !important;
  box-shadow: 0 28px 56px rgba(99, 102, 241, 0.3);
}
.float-card:hover::before {
  opacity: 0.4;
}
.card-1 {
  top: 40px; right: 60px;
  transform: rotate(3deg);
  z-index: 2;
}
.card-2 {
  bottom: 40px; right: 0;
  transform: rotate(-3deg);
  z-index: 1;
}
.float-card-img {
  width: 100%;
  height: 140px;
  border-radius: 10px;
  background-size: cover;
  background-position: center;
  background-color: #f1f5f9;
  margin-bottom: 10px;
}
.float-card-name {
  font-size: 14px;
  font-weight: 500;
  color: #1f2937;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.float-card-price {
  font-size: 18px;
  font-weight: 700;
  color: #f43f5e;
}
.float-card-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  background: var(--gradient-warm);
  color: #fff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  z-index: 2;
}

/* ============== 特色优势 ============== */
.features-section {
  padding: 30px 0 50px;
}
.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}
.feature-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  border: 1px solid transparent;
  overflow: hidden;
}
.feature-item::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.04), rgba(124, 58, 237, 0.04));
  opacity: 0;
  transition: opacity 0.3s;
  pointer-events: none;
}
.feature-item:hover {
  transform: translateY(-6px) scale(1.02);
  box-shadow: 0 16px 40px rgba(99, 102, 241, 0.18);
  border-color: rgba(99, 102, 241, 0.2);
}
.feature-item:hover::before {
  opacity: 1;
}
.feature-item:hover .feature-icon {
  transform: scale(1.1) rotate(-5deg);
}
.feature-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  position: relative;
  z-index: 1;
}
.feature-text {
  position: relative;
  z-index: 1;
}
.feature-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4px;
}
.feature-desc {
  font-size: 13px;
  color: #94a3b8;
}

/* ============== 通用 section header ============== */
.section-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 28px;
  flex-wrap: wrap;
  gap: 16px;
}
.section-title-group {
  display: flex;
  align-items: center;
  gap: 16px;
}
.section-title {
  font-size: 26px;
  font-weight: 700;
  color: #1f2937;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  letter-spacing: -0.5px;
}
.title-icon {
  font-size: 28px;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
}
.section-subtitle {
  font-size: 14px;
  color: #94a3b8;
  margin: 4px 0 0;
}
.more-btn {
  font-size: 14px;
  color: #6366f1;
  font-weight: 500;
}
.more-btn:hover {
  color: #818cf8;
}

/* ============== 限时秒杀 ============== */
.flash-section {
  padding: 40px 0;
}
.flash-icon {
  font-size: 36px;
  filter: drop-shadow(0 0 12px rgba(245, 158, 11, 0.5));
}
.countdown {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 20px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(20px);
}
.countdown-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}
.countdown-time {
  display: flex;
  align-items: center;
  gap: 6px;
}
.time-block {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
  padding: 0 6px;
  background: linear-gradient(135deg, #1f2937, #374151);
  color: #fff;
  border-radius: 6px;
  font-weight: 700;
  font-size: 15px;
  font-family: 'Courier New', monospace;
}
.time-sep {
  color: #94a3b8;
  font-weight: 700;
}

/* ============== 商品分类 ============== */
.category-section {
  padding: 50px 0;
}
.category-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 16px;
}
.category-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 24px 12px;
  background: #fff;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  border: 1px solid #f1f5f9;
  overflow: hidden;
}
.category-item::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.06), rgba(244, 63, 94, 0.04));
  opacity: 0;
  transition: opacity 0.3s;
}
.category-item::after {
  content: '';
  position: absolute;
  bottom: 0; left: 50%;
  transform: translateX(-50%);
  width: 0; height: 3px;
  background: var(--gradient-primary);
  border-radius: 2px;
  transition: width 0.3s;
}
.category-item:hover {
  transform: translateY(-8px);
  box-shadow: 0 20px 40px rgba(99, 102, 241, 0.18);
  border-color: transparent;
}
.category-item:hover::before {
  opacity: 1;
}
.category-item:hover::after {
  width: 60%;
}
.category-item:hover .category-arrow {
  opacity: 1;
  transform: translateX(0);
}
.category-icon-wrap {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.3s;
  z-index: 1;
}
.category-item:hover .category-icon-wrap {
  transform: scale(1.1) rotate(-5deg);
}
.category-emoji {
  font-size: 32px;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
}
.category-name {
  font-size: 14px;
  font-weight: 500;
  color: #475569;
  z-index: 1;
}
.category-item:hover .category-name {
  color: #6366f1;
  font-weight: 600;
}
.category-arrow {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateX(-10px);
  opacity: 0;
  color: #6366f1;
  transition: all 0.3s;
  margin-top: -8px;
}

/* ============== 商品 section ============== */
.products-section {
  padding: 50px 0;
}
.products-section.alt-bg {
  background: linear-gradient(180deg, transparent, rgba(99, 102, 241, 0.03), transparent);
}

/* ============== 品牌墙 ============== */
.brands-section {
  padding: 40px 0;
}
.brands-banner {
  position: relative;
  padding: 48px;
  border-radius: 24px;
  display: flex;
  align-items: center;
  gap: 60px;
  background:
    linear-gradient(135deg, rgba(255,255,255,0.85), rgba(240, 247, 255, 0.7)),
    radial-gradient(circle at 0% 0%, rgba(99, 102, 241, 0.12), transparent 50%),
    radial-gradient(circle at 100% 100%, rgba(244, 63, 94, 0.1), transparent 50%);
  border: 1px solid rgba(99, 102, 241, 0.15);
  box-shadow: 0 12px 40px rgba(99, 102, 241, 0.1);
  overflow: hidden;
}
.brands-banner::before {
  content: '';
  position: absolute;
  top: -40px; right: -40px;
  width: 200px; height: 200px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.2), transparent 70%);
  filter: blur(40px);
  pointer-events: none;
}
.brands-text {
  flex-shrink: 0;
  min-width: 240px;
}
.brands-text h2 {
  font-size: 36px;
  font-weight: 800;
  margin: 0 0 8px;
  letter-spacing: -1px;
}
.brands-text p {
  color: #64748b;
  margin: 0 0 16px;
  font-size: 14px;
  line-height: 1.6;
}
.brands-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.brand-chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 12px;
  border-radius: 999px;
  background: rgba(99, 102, 241, 0.08);
  color: #4f46e5;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid rgba(99, 102, 241, 0.15);
  transition: all 0.25s;
}
.brand-chip:hover {
  background: rgba(99, 102, 241, 0.15);
  transform: translateY(-1px);
}
.brands-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.brand-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: #fff;
  border-radius: 12px;
  font-weight: 500;
  color: #475569;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: all 0.3s;
  border: 1px solid transparent;
}
.brand-item:hover {
  transform: translateX(4px) translateY(-2px);
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.15);
  border-color: rgba(99, 102, 241, 0.2);
}
.brand-icon {
  font-size: 24px;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
}
.brand-name {
  font-size: 15px;
}

/* ============== 响应式 ============== */
@media (max-width: 1024px) {
  .hero-inner { grid-template-columns: 1fr; }
  .hero-float-cards { display: none; }
  .hero-title { font-size: 42px; }
  .features-grid { grid-template-columns: repeat(2, 1fr); }
  .category-grid { grid-template-columns: repeat(4, 1fr); }
  .brands-banner { flex-direction: column; gap: 30px; padding: 30px; }
}
@media (max-width: 640px) {
  .hero-section { padding: 30px 16px 50px; }
  .hero-title { font-size: 32px; }
  .hero-stats { gap: 16px; padding: 16px; }
  .stat-num { font-size: 22px; }
  .features-grid { grid-template-columns: 1fr; }
  .category-grid { grid-template-columns: repeat(3, 1fr); gap: 12px; }
  .brands-grid { grid-template-columns: 1fr; }
  .section-title { font-size: 22px; }
}
</style>
