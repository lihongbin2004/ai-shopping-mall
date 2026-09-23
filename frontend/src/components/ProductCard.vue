<template>
  <div class="product-card" @click="goToDetail">
    <div class="product-image">
      <div class="img-placeholder" v-if="!imgLoaded && !imgError">
        <el-icon :size="40"><Picture /></el-icon>
      </div>
      <img
        :src="imgSrc"
        class="card-img"
        :class="{ loaded: imgLoaded }"
        @load="imgLoaded = true"
        @error="imgError = true"
        v-show="imgLoaded || imgError"
      />
      <div class="image-error" v-if="imgError">
        <el-icon :size="40"><Picture /></el-icon>
        <span>暂无图片</span>
      </div>
      <!-- 标签角标 -->
      <div class="badges">
        <span v-if="product.sales >= 1000" class="badge badge-hot animate-pulse">🔥 爆款</span>
        <span v-else-if="product.sales >= 500" class="badge badge-hot">🔥 热卖</span>
        <span v-if="isNew" class="badge badge-new">✨ 新品</span>
      </div>
      <!-- 悬浮遮罩 -->
      <div class="hover-mask">
        <el-button type="primary" size="small" round>
          <el-icon><View /></el-icon> 查看详情
        </el-button>
      </div>
    </div>
    <div class="product-info">
      <h3 class="product-name text-ellipsis-2">{{ product.name }}</h3>
      <div class="product-price-row">
        <span class="price">&yen;<span class="price-symbol">{{ Number(product.price).toFixed(2) }}</span></span>
      </div>
      <div class="product-meta">
        <span class="sales">
          <el-icon><SoldOut /></el-icon> 已售 {{ formatSales(product.sales) }}
        </span>
        <el-icon class="fav-icon" @click.stop="toggleFav"><Star /></el-icon>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  product: {
    type: Object,
    required: true,
    default: () => ({})
  }
})

const router = useRouter()
const imgLoaded = ref(false)
const imgError = ref(false)

const imgSrc = computed(() => {
  return props.product.image || ''
})

// 是否新品（30天内上架）
const isNew = computed(() => {
  if (!props.product.createTime) return false
  const created = new Date(props.product.createTime).getTime()
  const now = Date.now()
  return (now - created) < 30 * 24 * 60 * 60 * 1000
})

function formatSales(n) {
  if (!n) return 0
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return n
}

function goToDetail() {
  const pid = props.product.productId || props.product.id
  router.push('/product/' + pid)
}

function toggleFav(e) {
  e.stopPropagation()
  // TODO: 收藏逻辑
}
</script>

<style scoped>
.product-card {
  position: relative;
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.4s;
  border: 1px solid transparent;
  height: 100%;
  display: flex;
  flex-direction: column;
  will-change: transform;
}
.product-card::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: var(--gradient-primary);
  opacity: 0;
  z-index: -1;
  filter: blur(16px);
  transition: opacity 0.4s;
  pointer-events: none;
}
.product-card:hover {
  transform: translateY(-8px) scale(1.01);
  box-shadow: 0 20px 40px rgba(99, 102, 241, 0.22);
  border-color: rgba(99, 102, 241, 0.25);
}
.product-card:hover::before { opacity: 0.35; }
.product-image {
  position: relative;
  width: 100%;
  padding-top: 100%;
  overflow: hidden;
  background:
    linear-gradient(135deg, #f1f5f9, #e2e8f0),
    radial-gradient(circle at 30% 30%, rgba(99, 102, 241, 0.06), transparent 60%);
}
.img-placeholder {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #cbd5e1;
}
.card-img {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.6s cubic-bezier(0.16, 1, 0.3, 1), filter 0.4s;
  opacity: 0;
}
.card-img.loaded {
  opacity: 1;
}
.product-card:hover .card-img.loaded {
  transform: scale(1.12);
  filter: saturate(1.1);
}
.image-error {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #94a3b8;
  font-size: 13px;
}
.badges {
  position: absolute;
  top: 12px;
  left: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  z-index: 2;
}
.badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.4;
  letter-spacing: 0.3px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}
.badge-hot {
  background: linear-gradient(135deg, #f43f5e, #fb923c);
  color: #fff;
}
.badge-new {
  background: linear-gradient(135deg, #06b6d4, #3b82f6);
  color: #fff;
}
.hover-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, transparent 40%, rgba(0, 0, 0, 0.5) 100%);
  opacity: 0;
  transition: opacity 0.3s;
}
.product-card:hover .hover-mask {
  opacity: 1;
}
.product-info {
  padding: 14px 14px 12px;
  display: flex;
  flex-direction: column;
  flex: 1;
}
.product-name {
  font-size: 14px;
  font-weight: 500;
  color: #1f2937;
  line-height: 1.5;
  margin: 0 0 10px;
  min-height: 42px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  transition: color 0.2s;
}
.product-card:hover .product-name {
  color: #6366f1;
}
.product-price-row {
  margin-bottom: 8px;
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.price {
  color: #f43f5e;
  font-weight: 700;
  font-size: 22px;
  display: inline-flex;
  align-items: baseline;
}
.price-symbol {
  font-size: 18px;
  font-weight: 700;
}
.product-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #94a3b8;
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px dashed #f1f5f9;
}
.sales {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.fav-icon {
  cursor: pointer;
  color: #cbd5e1;
  font-size: 16px;
  transition: all 0.2s;
  padding: 4px;
  border-radius: 50%;
}
.fav-icon:hover {
  color: #f59e0b;
  background: #fef3c7;
  transform: scale(1.1);
}
</style>
