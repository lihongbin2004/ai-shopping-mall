<template>
  <div class="cart-page">
    <Navbar />
    <div class="main-content">
      <div class="page-header reveal">
        <h2 class="page-title">
          <el-icon><ShoppingCart /></el-icon>
          <span>我的购物车</span>
          <el-tag v-if="cartStore.cartItems.length" type="info" effect="plain" round class="count-tag">
            {{ cartStore.cartItems.length }} 件
          </el-tag>
        </h2>
        <p class="page-sub">精心挑选的商品正等待你的确认</p>
      </div>

      <el-alert
        v-if="offShelfItems.length"
        type="error"
        :closable="false"
        show-icon
        class="reveal"
        style="margin-bottom: 16px;"
      >
        <template #title>
          购物车中有 {{ offShelfItems.length }} 件商品已下架，无法结算，请移除
        </template>
        <template #default>
          <el-button type="danger" size="small" plain @click="removeAllOffShelf">
            移除所有下架商品（{{ offShelfItems.length }}）
          </el-button>
        </template>
      </el-alert>

      <div v-if="cartStore.cartItems.length" class="cart-container glass reveal">
        <div class="cart-header">
          <el-checkbox v-model="allChecked" :disabled="offShelfItems.length === cartStore.cartItems.length" @change="handleAllCheck">全选</el-checkbox>
          <span class="col-product">商品</span>
          <span class="col-price">单价</span>
          <span class="col-quantity">数量</span>
          <span class="col-total">小计</span>
          <span class="col-action">操作</span>
        </div>
        <div
          v-for="(item, idx) in cartStore.cartItems"
          :key="item.id"
          class="cart-item"
          :class="{ 'off-shelf': isOffShelf(item) }"
          :style="{ animationDelay: idx * 0.05 + 's' }"
        >
          <el-checkbox v-model="item.checked" :disabled="isOffShelf(item)" @change="() => handleCheck(item)" />
          <div class="item-product">
            <div class="item-img-wrap">
              <el-image :src="item.productImage" fit="cover" class="item-img" />
            </div>
            <div class="item-info">
              <p class="item-name">
                {{ item.productName }}
                <el-tag v-if="isOffShelf(item)" type="danger" size="small" effect="dark" style="margin-left:8px;vertical-align:middle">已下架</el-tag>
              </p>
              <p v-if="item.specDesc" class="item-spec spec-desc">{{ item.specDesc }}</p>
              <p v-if="isOffShelf(item)" class="item-offshelf-tip">该商品已下架，无法购买</p>
              <p v-else-if="!item.specDesc" class="item-spec">库存 {{ item.stock || 0 }} 件</p>
            </div>
          </div>
          <span class="item-price">¥{{ Number(item.productPrice).toFixed(2) }}</span>
          <el-input-number
            v-model="item.quantity"
            :min="1"
            :max="item.stock || 99"
            size="default"
            :disabled="isOffShelf(item)"
            @change="(val) => handleQuantityChange(item, val)"
          />
          <span class="item-total">¥{{ (Number(item.productPrice) * item.quantity).toFixed(2) }}</span>
          <el-button text type="danger" @click="handleRemove(item)" class="del-btn">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
        <div class="cart-footer">
          <div class="footer-left">
            <el-button text @click="handleClearAll">
              <el-icon><Delete /></el-icon> 清空购物车
            </el-button>
          </div>
          <div class="footer-right">
            <span class="total-info">
              已选 <strong class="checked-count">{{ cartStore.checkedCount }}</strong> 件
              合计：<strong class="total-price">¥{{ cartStore.totalPrice.toFixed(2) }}</strong>
            </span>
            <el-button
              type="primary"
              size="large"
              :disabled="cartStore.checkedCount === 0 || offShelfCheckedCount > 0"
              @click="goCheckout"
              class="checkout-btn"
            >
              {{ offShelfCheckedCount > 0 ? '含下架商品' : '去结算 →' }}
            </el-button>
          </div>
        </div>
      </div>
      <div v-else class="empty-cart reveal-scale">
        <el-empty description="购物车是空的，去添加心仪好物吧" :image-size="140">
          <el-button type="primary" size="large" @click="$router.push('/products')">
            <el-icon><ShoppingCart /></el-icon> 去购物
          </el-button>
        </el-empty>
      </div>
    </div>
    <Footer />
  </div>
</template>

<script setup>
import { computed, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ShoppingCart, Delete } from '@element-plus/icons-vue'
import Navbar from '@/components/Navbar.vue'
import Footer from '@/components/Footer.vue'
import { useReveal } from '@/composables/useReveal'
import { useCartStore } from '@/stores/cart'
import { toggleCartCheck, toggleAllCheck } from '@/api/cart'

const router = useRouter()
const cartStore = useCartStore()

useReveal(null)
const { refresh: refreshReveal } = useReveal(null)

// 0 = 下架；1/null = 正常
function isOffShelf(item) {
  return item.productStatus !== undefined && item.productStatus === 0
}
const offShelfItems = computed(() => cartStore.cartItems.filter(isOffShelf))
const offShelfCheckedCount = computed(() => offShelfItems.value.filter(i => i.checked).length)

const allChecked = computed({
  get: () => {
    const checkable = cartStore.cartItems.filter(i => !isOffShelf(i))
    return checkable.length > 0 && checkable.every(item => item.checked)
  },
  set: (val) => {
    cartStore.cartItems.forEach(item => {
      if (!isOffShelf(item)) item.checked = val
    })
    handleAllCheck(val)
  }
})

async function handleAllCheck(val) {
  try { await toggleAllCheck(val ? 1 : 0) } catch {}
}

async function handleCheck(item) {
  try { await toggleCartCheck(item.id, item.checked ? 1 : 0) } catch {}
}

async function handleQuantityChange(item, val) {
  try {
    await cartStore.updateItem(item.id, { quantity: val })
  } catch {
    // interceptor handled
  }
}

async function handleRemove(item) {
  await cartStore.removeItem(item.id)
}

async function handleClearAll() {
  try {
    await ElMessageBox.confirm('确定清空购物车？', '提示', { type: 'warning' })
  } catch { return }
  await cartStore.clearAll()
}

async function removeAllOffShelf() {
  try {
    await ElMessageBox.confirm(`确定移除所有 ${offShelfItems.value.length} 件已下架商品？`, '提示', { type: 'warning' })
  } catch { return }
  for (const item of offShelfItems.value) {
    await cartStore.removeItem(item.id)
  }
  ElMessage.success('已下架商品已移除')
}

function goCheckout() {
  if (offShelfCheckedCount.value > 0) {
    ElMessage.warning('请先取消勾选或移除已下架商品')
    return
  }
  router.push('/order/confirm')
}

onMounted(() => {
  cartStore.fetchCartList()
})
</script>

<style scoped>
.cart-page {
  min-height: 100vh;
  background:
    radial-gradient(ellipse 800px 400px at 0% 0%, rgba(99, 102, 241, 0.06), transparent 60%),
    radial-gradient(ellipse 600px 600px at 100% 30%, rgba(244, 63, 94, 0.04), transparent 60%),
    linear-gradient(180deg, #f8fafc 0%, #ffffff 100%);
}
.main-content { max-width: 1240px; margin: 0 auto; padding: 24px 20px 40px; }

.page-header { margin-bottom: 24px; }
.page-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 28px;
  font-weight: 800;
  color: #1f2937;
  margin: 0 0 6px;
  letter-spacing: -0.5px;
}
.page-title .el-icon {
  font-size: 32px;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.count-tag {
  font-size: 13px !important;
  font-weight: 600;
  padding: 4px 12px !important;
  margin-left: 4px;
}
.page-sub {
  margin: 0;
  color: #94a3b8;
  font-size: 14px;
}

.cart-container {
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(99, 102, 241, 0.1);
  box-shadow: 0 8px 32px rgba(99, 102, 241, 0.06);
}
.cart-header {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  background: linear-gradient(135deg, #f8fafc, #f1f5f9);
  font-size: 14px;
  color: #475569;
  font-weight: 500;
  gap: 12px;
  border-bottom: 1px solid #e2e8f0;
}
.cart-header .el-checkbox { min-width: 60px; }
.col-product { flex: 2; }
.col-price, .col-quantity, .col-total, .col-action { flex: 1; text-align: center; }

.cart-item {
  display: flex;
  align-items: center;
  padding: 18px 20px;
  border-bottom: 1px solid #f1f5f9;
  gap: 12px;
  transition: all 0.3s;
  opacity: 0;
  animation: slide-up 0.5s ease-out forwards;
}
.cart-item:hover {
  background: rgba(99, 102, 241, 0.03);
}
.cart-item:last-child { border-bottom: none; }
.cart-item.off-shelf { background: rgba(254, 226, 226, 0.5); opacity: 0.75; }
.cart-item .el-checkbox { min-width: 60px; }

.item-product { flex: 2; display: flex; gap: 14px; align-items: center; }
.item-img-wrap {
  width: 88px; height: 88px;
  border-radius: 10px;
  overflow: hidden;
  background: linear-gradient(135deg, #f1f5f9, #e2e8f0);
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
  transition: transform 0.3s;
}
.cart-item:hover .item-img-wrap { transform: scale(1.05); }
.item-img { width: 88px; height: 88px; }
.item-info { flex: 1; min-width: 0; }
.item-name {
  margin: 0 0 6px;
  font-size: 15px;
  color: #1f2937;
  font-weight: 500;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  transition: color 0.2s;
}
.cart-item:hover .item-name { color: var(--primary); }
.item-spec {
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
}
.item-offshelf-tip { margin: 4px 0 0; font-size: 12px; color: #f43f5e; }

.item-price, .item-total {
  flex: 1;
  text-align: center;
  color: #f43f5e;
  font-weight: 600;
  font-size: 15px;
}
.item-total {
  font-size: 17px;
  font-weight: 700;
}
.del-btn {
  flex: 0 0 40px;
  border-radius: 8px;
  transition: all 0.25s;
}
.del-btn:hover {
  background: rgba(244, 63, 94, 0.1);
  transform: scale(1.1);
}

.cart-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 24px;
  background: linear-gradient(180deg, #f8fafc, #ffffff);
  border-top: 1px solid #f1f5f9;
}
.footer-right { display: flex; align-items: center; gap: 20px; }
.total-info { font-size: 14px; color: #475569; }
.checked-count {
  color: var(--primary);
  font-weight: 700;
  margin: 0 2px;
}
.total-price {
  font-size: 26px;
  background: var(--gradient-warm);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  font-weight: 800;
  margin-left: 4px;
}
.checkout-btn {
  height: 44px !important;
  font-weight: 600;
  padding: 0 28px !important;
  font-size: 15px !important;
  letter-spacing: 0.5px;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.checkout-btn:hover:not([disabled]) {
  transform: translateY(-2px) scale(1.02);
  box-shadow: 0 12px 28px rgba(99, 102, 241, 0.4);
}

.empty-cart {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  padding: 60px 20px;
  border: 1px solid rgba(99, 102, 241, 0.08);
  box-shadow: 0 8px 32px rgba(99, 102, 241, 0.06);
}
.empty-cart :deep(.el-button) {
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.empty-cart :deep(.el-button:hover) {
  transform: translateY(-2px) scale(1.05);
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.3);
}

@media (max-width: 768px) {
  .cart-header { display: none; }
  .cart-item {
    flex-wrap: wrap;
    padding: 14px;
  }
  .item-product { flex: 1 1 100%; margin-bottom: 8px; }
  .item-price, .item-total { flex: 1; }
  .page-title { font-size: 22px; }
}
</style>
