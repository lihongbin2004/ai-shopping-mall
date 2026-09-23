<template>
  <div class="order-confirm-page">
    <Navbar />
    <div class="main-content" v-loading="loading">
      <h2 class="page-title">确认订单</h2>

      <!-- 收货信息 -->
      <div class="section">
        <div class="section-header">
          <h3>收货信息</h3>
          <div class="section-actions">
            <el-button text type="primary" @click="openAddAddress">
              <el-icon><Plus /></el-icon> 新增收货地址
            </el-button>
          </div>
        </div>
        <div class="address-list">
          <div
            v-for="addr in addresses"
            :key="addr.id"
            :class="['address-card', { active: selectedAddress?.id === addr.id }]"
            @click="selectedAddress = addr"
          >
            <div class="addr-info">
              <span class="addr-name">{{ addr.receiver }}</span>
              <span class="addr-phone">{{ addr.phone }}</span>
              <el-tag v-if="addr.isDefault === 1" size="small" type="primary">默认</el-tag>
            </div>
            <p class="addr-detail">{{ addr.province }}{{ addr.city }}{{ addr.district }}{{ addr.detail }}</p>
            <div class="addr-actions" @click.stop>
              <el-button link type="primary" size="small" @click="openEditAddress(addr)">
                <el-icon><Edit /></el-icon> 编辑
              </el-button>
              <el-button link type="danger" size="small" @click="removeAddress(addr)">
                <el-icon><Delete /></el-icon> 删除
              </el-button>
              <el-button v-if="addr.isDefault !== 1" link type="primary" size="small" @click="setAsDefault(addr)">
                设为默认
              </el-button>
            </div>
          </div>
          <el-empty v-if="!addresses.length && !loading" description="暂无收货地址，请添加" />
        </div>
      </div>

      <!-- 商品信息 -->
      <div class="section">
        <h3>商品信息</h3>
        <div v-for="item in orderItems" :key="item.id" class="order-item">
          <el-image :src="item.productImage || item.image" class="item-img" />
          <div class="item-info">
            <p class="item-name">{{ item.productName }}</p>
          </div>
          <span class="item-price">¥{{ item.productPrice.toFixed(2) }}</span>
          <span class="item-qty">x{{ item.quantity }}</span>
          <span class="item-total">¥{{ (item.productPrice * item.quantity).toFixed(2) }}</span>
        </div>
      </div>

      <!-- 优惠券 -->
      <div class="section">
        <h3>优惠券</h3>
        <div class="coupon-select">
          <el-select v-model="selectedCouponId" placeholder="选择优惠券" clearable style="width: 300px" :no-data-text="couponLoading ? '加载中...' : '暂无可用优惠券'">
            <el-option
              v-for="c in availableCoupons"
              :key="c.id"
              :label="`${c.couponName} - 满${c.minAmount}减${c.amount}`"
              :value="c.id"
            />
          </el-select>
          <el-button v-if="!availableCoupons.length && !couponLoading" text type="primary" @click="goClaimCoupon" style="margin-left:8px">
            去领取优惠券
          </el-button>
        </div>
      </div>

      <!-- 备注 -->
      <div class="section">
        <h3>订单备注</h3>
        <el-input v-model="remark" type="textarea" :rows="2" placeholder="选填，可输入备注信息" />
      </div>

      <!-- 支付方式 -->
      <div class="section">
        <h3>支付方式</h3>
        <el-radio-group v-model="payment">
          <el-radio value="支付宝">支付宝</el-radio>
        </el-radio-group>
        <p class="payment-tip">当前仅支持支付宝沙箱支付</p>
      </div>

      <!-- 汇总 -->
      <div class="order-summary">
        <div class="summary-row">
          <span>商品总计</span>
          <span>¥{{ totalAmount.toFixed(2) }}</span>
        </div>
        <div class="summary-row" v-if="couponDiscount">
          <span>优惠券减</span>
          <span class="discount">-¥{{ couponDiscount.toFixed(2) }}</span>
        </div>
        <div class="summary-row total">
          <span>应付金额</span>
          <span class="final-price">¥{{ finalAmount.toFixed(2) }}</span>
        </div>
        <el-button type="primary" size="large" :loading="submitting" :disabled="hasOffShelf" @click="submitOrder">
          {{ hasOffShelf ? '存在已下架商品' : '提交订单' }}
        </el-button>
      </div>
    </div>

    <!-- 新增 / 编辑地址弹窗 -->
    <el-dialog v-model="showAddressDialog" :title="addressForm.id ? '编辑地址' : '新增地址'" width="520px">
      <el-form :model="addressForm" label-width="80px" size="default">
        <el-form-item label="收货人">
          <el-input v-model="addressForm.receiver" placeholder="姓名" maxlength="20" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="addressForm.phone" placeholder="11位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="所在地区">
          <el-input v-model="addressForm.province" placeholder="省" style="width:30%;margin-right:3%" />
          <el-input v-model="addressForm.city" placeholder="市" style="width:30%;margin-right:3%" />
          <el-input v-model="addressForm.district" placeholder="区/县" style="width:30%" />
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="addressForm.detail" type="textarea" :rows="2" placeholder="街道、楼栋、门牌号" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="addressForm.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddressDialog = false">取消</el-button>
        <el-button type="primary" :loading="addressSaving" @click="saveAddress">保存</el-button>
      </template>
    </el-dialog>

    <Footer />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import Navbar from '@/components/Navbar.vue'
import Footer from '@/components/Footer.vue'
import { useCartStore } from '@/stores/cart'
import { createOrder } from '@/api/order'
import { getAddressList, addAddress, updateAddress, deleteAddress, setDefaultAddress } from '@/api/address'
import { getAvailableCoupons } from '@/api/coupon'

const router = useRouter()
const cartStore = useCartStore()
const loading = ref(false)
const submitting = ref(false)
const couponLoading = ref(false)
const addresses = ref([])
const selectedAddress = ref(null)
const orderItems = ref([])
const availableCoupons = ref([])
const selectedCouponId = ref('')
const remark = ref('')
const showAddressDialog = ref(false)
const addressSaving = ref(false)
const payment = ref('支付宝')

const addressForm = reactive({
  id: null,
  receiver: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: 0
})

function resetAddressForm(addr) {
  if (addr) {
    addressForm.id = addr.id
    addressForm.receiver = addr.receiver || ''
    addressForm.phone = addr.phone || ''
    addressForm.province = addr.province || ''
    addressForm.city = addr.city || ''
    addressForm.district = addr.district || ''
    addressForm.detail = addr.detail || ''
    addressForm.isDefault = addr.isDefault ?? 0
  } else {
    addressForm.id = null
    addressForm.receiver = ''
    addressForm.phone = ''
    addressForm.province = ''
    addressForm.city = ''
    addressForm.district = ''
    addressForm.detail = ''
    addressForm.isDefault = addresses.value.length ? 0 : 1
  }
}

async function saveAddress() {
  if (!addressForm.receiver) return ElMessage.warning('请输入收货人')
  if (!/^1\d{10}$/.test(addressForm.phone)) return ElMessage.warning('请输入正确的手机号')
  if (!addressForm.province || !addressForm.city) return ElMessage.warning('请填写省/市')
  if (!addressForm.detail) return ElMessage.warning('请填写详细地址')
  addressSaving.value = true
  try {
    const isEdit = !!addressForm.id
    const res = isEdit
      ? await updateAddress({ ...addressForm })
      : await addAddress({ ...addressForm })
    ElMessage.success(isEdit ? '地址已更新' : '地址已添加')
    showAddressDialog.value = false
    // 重新拉取地址列表
    const addrRes = await getAddressList()
    addresses.value = addrRes.data || []
    const target = res.data
    selectedAddress.value = target
      ? addresses.value.find(a => a.id === target.id)
      : (addresses.value.find(a => a.isDefault === 1) || addresses.value[0] || null)
  } catch {
    // interceptor handled
  } finally {
    addressSaving.value = false
  }
}

function openAddAddress() {
  resetAddressForm(null)
  showAddressDialog.value = true
}

function openEditAddress(addr) {
  resetAddressForm(addr)
  showAddressDialog.value = true
}

async function removeAddress(addr) {
  try {
    await ElMessageBox.confirm(`确定删除「${addr.receiver}」的收货地址？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteAddress(addr.id)
    ElMessage.success('地址已删除')
    const addrRes = await getAddressList()
    addresses.value = addrRes.data || []
    if (selectedAddress.value?.id === addr.id) {
      selectedAddress.value = addresses.value.find(a => a.isDefault === 1) || addresses.value[0] || null
    }
  } catch {
    // interceptor handled
  }
}

async function setAsDefault(addr) {
  try {
    await setDefaultAddress({ id: addr.id })
    ElMessage.success('已设为默认地址')
    const addrRes = await getAddressList()
    addresses.value = addrRes.data || []
    selectedAddress.value = addresses.value.find(a => a.id === addr.id) || selectedAddress.value
  } catch {
    // interceptor handled
  }
}

function goClaimCoupon() {
  router.push('/coupon')
}

const couponDiscount = computed(() => {
  if (!selectedCouponId.value) return 0
  const coupon = availableCoupons.value.find(c => c.id === selectedCouponId.value)
  if (coupon) return Number(coupon.amount || coupon.discount || 0)
  return 0
})

// 已下架商品：cart item 上 productStatus=0 视为下架
function isOffShelf(item) {
  return item && item.productStatus !== undefined && item.productStatus === 0
}
const offShelfItems = computed(() => orderItems.value.filter(isOffShelf))
const hasOffShelf = computed(() => offShelfItems.value.length > 0)

const totalAmount = computed(() => {
  return orderItems.value.reduce((sum, item) => sum + Number(item.productPrice) * item.quantity, 0)
})

const finalAmount = computed(() => {
  const discount = couponDiscount.value
  return Math.max(0, totalAmount.value - discount)
})

async function loadData() {
  loading.value = true
  couponLoading.value = true
  try {
    orderItems.value = cartStore.cartItems.filter(item => item.checked)
    if (!orderItems.value.length) {
      ElMessage.warning('请先选择商品')
      router.push('/cart')
      return
    }
    const [addrRes, couponRes] = await Promise.all([
      getAddressList(),
      getAvailableCoupons({ amount: totalAmount.value }).catch(() => ({ data: [] }))
    ])
    addresses.value = addrRes.data || []
    selectedAddress.value = addresses.value.find(a => a.isDefault === 1) || addresses.value[0] || null
    availableCoupons.value = couponRes.data || []
  } catch {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
    couponLoading.value = false
  }
}

async function submitOrder() {
  if (!selectedAddress.value) {
    ElMessage.warning('请选择收货地址')
    return
  }
  if (!orderItems.value.length) {
    ElMessage.warning('订单无商品')
    return
  }
  if (hasOffShelf.value) {
    ElMessage.error('订单中包含已下架商品，请先到购物车移除')
    return
  }
  submitting.value = true
  try {
    const data = {
      addressId: selectedAddress.value.id,
      payment: payment.value || '在线支付',
      couponId: selectedCouponId.value || null,
      remark: remark.value,
      cartIds: orderItems.value.map(item => item.id)
    }
    console.log('[OrderConfirm] 提交订单参数:', data)
    const res = await createOrder(data)
    console.log('[OrderConfirm] 提交订单结果:', res)
    const orderId = res.data?.id || res.data?.orderId
    if (!orderId) {
      ElMessage.error('下单成功但未返回订单ID，请前往订单列表查看')
      submitting.value = false
      setTimeout(() => router.push('/orders'), 800)
      return
    }
    ElMessage.success({
      message: `下单成功！订单号：${res.data?.orderNo || orderId}`,
      duration: 2500
    })
    try { await cartStore.fetchCartList() } catch {}
    submitting.value = false
    setTimeout(() => {
      router.push(`/order/${orderId}`).catch(err => {
        console.error('[OrderConfirm] 跳转失败:', err)
        window.location.href = `/order/${orderId}`
      })
    }, 300)
  } catch (e) {
    console.error('[OrderConfirm] 提交订单失败:', e)
    const msg = e?.response?.data?.msg || e?.message || '提交失败，请重试'
    ElMessage({ type: 'error', message: msg, duration: 4000 })
    submitting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.order-confirm-page { min-height: 100vh; background: #f5f7fa; }
.main-content { max-width: 1200px; margin: 0 auto; padding: 20px; }
.page-title { font-size: 22px; margin: 0 0 20px; }
.section { background: #fff; border-radius: 8px; padding: 20px; margin-bottom: 16px; }
.section h3 { margin: 0 0 16px; font-size: 16px; padding-left: 10px; border-left: 3px solid #409eff; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.section-header h3 { margin: 0; }
.section-actions { display: flex; gap: 8px; }
.address-list { display: flex; flex-wrap: wrap; gap: 12px; }
.address-card { width: 280px; padding: 14px; border: 2px solid #e4e7ed; border-radius: 8px; cursor: pointer; transition: all 0.2s; position: relative; }
.address-card:hover, .address-card.active { border-color: #409eff; }
.address-card.active { background: #f0f5ff; }
.addr-info { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.addr-name { font-weight: 500; }
.addr-phone { color: #909399; }
.addr-detail { margin: 0 0 8px; font-size: 13px; color: #606266; }
.addr-actions { display: flex; gap: 4px; padding-top: 8px; border-top: 1px dashed #eee; }
.order-item { display: flex; align-items: center; gap: 16px; padding: 12px 0; border-bottom: 1px solid #f0f0f0; }
.order-item:last-child { border-bottom: none; }
.item-img { width: 60px; height: 60px; border-radius: 6px; }
.item-info { flex: 1; }
.item-name { margin: 0; }
.item-price, .item-total { color: #f56c6c; font-weight: 500; }
.item-qty { color: #909399; }
.order-summary { background: #fff; border-radius: 8px; padding: 20px; text-align: right; }
.summary-row { display: flex; justify-content: flex-end; gap: 20px; margin-bottom: 8px; font-size: 14px; }
.summary-row.total { font-size: 18px; font-weight: bold; margin-bottom: 16px; }
.discount { color: #67c23a; }
.final-price { color: #f56c6c; font-size: 24px; }
.payment-tip { color: #909399; font-size: 12px; margin: 8px 0 0; }
</style>