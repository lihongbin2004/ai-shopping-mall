<template>
  <div class="coupon-page">
    <Navbar />
    <div class="main-content">
      <h2 class="page-title">优惠券中心</h2>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="可领取" name="available">
          <div class="coupon-list" v-loading="loading">
            <div v-for="c in availableCoupons" :key="c.id" class="coupon-card">
              <div class="coupon-left">
                <span class="coupon-amount">¥{{ c.amount }}</span>
                <span class="coupon-condition">满{{ c.minAmount }}可用</span>
              </div>
              <div class="coupon-center">
                <h4>{{ c.name }}</h4>
                <p class="coupon-time">
                  {{ formatTime(c.startTime) }} ~ {{ formatTime(c.endTime) }}
                </p>
              </div>
              <div class="coupon-right">
                <el-button
                  type="primary"
                  :disabled="c.claimed"
                  @click="handleClaim(c)"
                >
                  {{ c.claimed ? '已领取' : '立即领取' }}
                </el-button>
              </div>
            </div>
            <el-empty v-if="!availableCoupons.length && !loading" description="暂无可领取的优惠券" />
          </div>
        </el-tab-pane>
        <el-tab-pane label="我的优惠券" name="mine">
          <div class="coupon-list" v-loading="loadingMine">
            <div v-for="c in myCoupons" :key="c.id" class="coupon-card mine">
              <div class="coupon-left">
                <span class="coupon-amount">¥{{ c.amount }}</span>
                <span class="coupon-condition">满{{ c.minAmount }}可用</span>
              </div>
              <div class="coupon-center">
                <h4>{{ c.couponName }}</h4>
                <p :class="c.status === 1 ? 'used' : c.status === 2 ? 'expired' : ''">
                  {{ statusText(c.status) }}
                </p>
                <p class="coupon-time">{{ formatTime(c.endTime) }} 到期</p>
              </div>
            </div>
            <el-empty v-if="!myCoupons.length && !loadingMine" description="暂无优惠券" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
    <Footer />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import Navbar from '@/components/Navbar.vue'
import Footer from '@/components/Footer.vue'
import { getCouponList, claimCoupon, getMyCoupons } from '@/api/coupon'

const activeTab = ref('available')
const loading = ref(false)
const loadingMine = ref(false)
const availableCoupons = ref([])
const myCoupons = ref([])

function statusText(status) {
  const map = { 0: '未使用', 1: '已使用', 2: '已过期' }
  return map[status] || '未使用'
}

function formatTime(t) {
  if (!t) return '-'
  const d = typeof t === 'string' ? new Date(t) : t
  if (isNaN(d.getTime())) return String(t)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadAvailable() {
  loading.value = true
  try {
    const res = await getCouponList({ status: 'available' })
    availableCoupons.value = res.data || []
  } catch {} finally {
    loading.value = false
  }
}

async function loadMine() {
  loadingMine.value = true
  try {
    const res = await getMyCoupons()
    myCoupons.value = res.data || []
  } catch {} finally {
    loadingMine.value = false
  }
}

async function handleClaim(coupon) {
  try {
    await claimCoupon(coupon.id)
    ElMessage.success('领取成功')
    loadAvailable()
    loadMine()
  } catch {}
}

onMounted(() => {
  loadAvailable()
  loadMine()
})
</script>

<style scoped>
.coupon-page { min-height: 100vh; background: #f5f7fa; }
.main-content { max-width: 1200px; margin: 0 auto; padding: 20px; }
.page-title { font-size: 22px; margin: 0 0 20px; }
.coupon-list { display: flex; flex-direction: column; gap: 12px; }
.coupon-card { display: flex; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.coupon-left { width: 120px; display: flex; flex-direction: column; align-items: center; justify-content: center; background: linear-gradient(135deg, #f093fb, #f5576c); color: #fff; padding: 16px; }
.coupon-card.mine .coupon-left { background: linear-gradient(135deg, #4facfe, #00f2fe); }
.coupon-amount { font-size: 28px; font-weight: bold; }
.coupon-condition { font-size: 12px; opacity: 0.85; }
.coupon-center { flex: 1; padding: 16px; }
.coupon-center h4 { margin: 0 0 4px; font-size: 15px; }
.coupon-center p { margin: 4px 0; font-size: 13px; color: #909399; }
.coupon-center .used { color: #c0c4cc; }
.coupon-center .expired { color: #f56c6c; }
.coupon-time { font-size: 12px; }
.coupon-right { display: flex; align-items: center; padding: 16px; }
</style>