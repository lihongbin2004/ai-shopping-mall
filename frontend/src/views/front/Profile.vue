<template>
  <div class="profile-page">
    <Navbar />
    <div class="main-content">
      <h2 class="page-title">个人中心</h2>

      <el-row :gutter="20">
        <!-- 左侧：用户卡片 + 快捷入口 -->
        <el-col :span="7">
          <div class="profile-card">
            <div class="avatar-section">
              <el-avatar :size="90" :src="userStore.avatar">
                {{ userStore.nickname?.charAt(0)?.toUpperCase() }}
              </el-avatar>
              <h3>{{ userStore.nickname }}</h3>
              <p class="user-id">用户ID：{{ stats.user?.id || '-' }}</p>
              <div class="badges">
                <el-tag :type="stats.user?.role === 'ADMIN' ? 'danger' : 'primary'" size="small">
                  {{ stats.roleLabel }}
                </el-tag>
                <el-tag :type="stats.user?.status === 1 ? 'success' : 'info'" size="small">
                  {{ stats.statusLabel }}
                </el-tag>
              </div>
            </div>
            <el-divider />
            <div class="profile-stats">
              <div class="stat-item" @click="$router.push('/orders')">
                <span class="stat-num">{{ stats.totalOrders || 0 }}</span>
                <span class="stat-label">订单</span>
              </div>
              <div class="stat-item" @click="$router.push('/refunds')">
                <span class="stat-num">↩</span>
                <span class="stat-label">退款</span>
              </div>
              <div class="stat-item" @click="$router.push('/favorites')">
                <span class="stat-num">{{ stats.favCount || 0 }}</span>
                <span class="stat-label">收藏</span>
              </div>
              <div class="stat-item" @click="$router.push('/coupon')">
                <span class="stat-num">{{ stats.couponCount || 0 }}</span>
                <span class="stat-label">优惠券</span>
              </div>
            </div>
          </div>

          <!-- 账户信息 -->
          <div class="info-card">
            <div class="info-card-title">账户信息</div>
            <ul class="info-list">
              <li>
                <span class="lbl">注册时间</span>
                <span class="val">{{ formatDate(stats.user?.createTime) || '-' }}</span>
              </li>
              <li>
                <span class="lbl">用户ID</span>
                <span class="val">{{ stats.user?.id || '-' }}</span>
              </li>
              <li>
                <span class="lbl">角色</span>
                <span class="val">{{ stats.roleLabel }}</span>
              </li>
              <li>
                <span class="lbl">账户状态</span>
                <span class="val" :class="stats.user?.status === 1 ? 'ok' : 'off'">
                  {{ stats.statusLabel }}
                </span>
              </li>
            </ul>
          </div>
        </el-col>

        <!-- 右侧：所有可显示信息 -->
        <el-col :span="17">
          <!-- 资料完整度 + 消费统计 -->
          <el-row :gutter="20">
            <el-col :span="12">
              <el-card>
                <template #header>
                  <span><el-icon><DataAnalysis /></el-icon> 资料完整度</span>
                </template>
                <div class="complete-bar">
                  <el-progress
                    :percentage="Math.round((stats.profileComplete / stats.profileTotal) * 100)"
                    :stroke-width="14"
                    :color="progressColor"
                  />
                </div>
                <div class="complete-tips">
                  <div class="tip" :class="{ done: hasPhone }">
                    <el-icon><Phone v-if="hasPhone" /><Warning v-else /></el-icon>
                    <span>已绑定手机号</span>
                  </div>
                  <div class="tip" :class="{ done: hasEmail }">
                    <el-icon><Message v-if="hasEmail" /><Warning v-else /></el-icon>
                    <span>已设置邮箱</span>
                  </div>
                  <div class="tip" :class="{ done: hasAvatar }">
                    <el-icon><User v-if="hasAvatar" /><Warning v-else /></el-icon>
                    <span>已设置头像</span>
                  </div>
                  <div class="tip" :class="{ done: hasDefaultAddress }">
                    <el-icon><Location v-if="hasDefaultAddress" /><Warning v-else /></el-icon>
                    <span>已设置默认地址</span>
                  </div>
                </div>
              </el-card>
            </el-col>

            <el-col :span="12">
              <el-card>
                <template #header>
                  <span><el-icon><Coin /></el-icon> 消费统计</span>
                </template>
                <div class="spend-grid">
                  <div class="spend-item">
                    <div class="num">¥{{ formatMoney(stats.totalSpent) }}</div>
                    <div class="lbl">累计消费</div>
                  </div>
                  <div class="spend-item">
                    <div class="num">{{ stats.finishedOrders || 0 }}</div>
                    <div class="lbl">已完成</div>
                  </div>
                  <div class="spend-item">
                    <div class="num">{{ stats.pendingOrders || 0 }}</div>
                    <div class="lbl">待付款</div>
                  </div>
                  <div class="spend-item">
                    <div class="num">{{ stats.shippedOrders || 0 }}</div>
                    <div class="lbl">已发货</div>
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <!-- 默认地址 + 评价统计 -->
          <el-row :gutter="20" style="margin-top: 16px">
            <el-col :span="12">
              <el-card>
                <template #header>
                  <div class="card-header">
                    <span><el-icon><Location /></el-icon> 默认收货地址</span>
                    <el-button text type="primary" @click="openAddressDialog">管理地址</el-button>
                  </div>
                </template>
                <div v-if="stats.defaultAddress" class="address-box">
                  <div class="addr-row">
                    <el-tag size="small" type="danger" effect="dark">默认</el-tag>
                    <span class="receiver">{{ stats.defaultAddress.receiver }}</span>
                    <span class="phone">{{ stats.defaultAddress.phone }}</span>
                  </div>
                  <div class="addr-detail">
                    {{ stats.defaultAddress.province }}
                    {{ stats.defaultAddress.city }}
                    {{ stats.defaultAddress.district }}
                    {{ stats.defaultAddress.detail }}
                  </div>
                </div>
                <div v-else class="empty">
                  <el-icon><LocationInformation /></el-icon>
                  <p>尚未设置默认收货地址</p>
                  <el-button type="primary" size="small" @click="openAddressDialog">
                    去添加
                  </el-button>
                </div>
              </el-card>
            </el-col>

            <el-col :span="12">
              <el-card>
                <template #header>
                  <span><el-icon><ChatLineRound /></el-icon> 我的评价</span>
                </template>
                <div class="review-stat">
                  <div class="big-num">{{ stats.reviewCount || 0 }}</div>
                  <div class="lbl">条评价</div>
                </div>
                <el-divider />
                <ul class="info-list compact">
                  <li>
                    <span class="lbl">收藏商品</span>
                    <span class="val">{{ stats.favCount || 0 }} 件</span>
                  </li>
                  <li>
                    <span class="lbl">可用优惠券</span>
                    <span class="val">{{ stats.couponCount || 0 }} 张</span>
                  </li>
                  <li>
                    <span class="lbl">订单总数</span>
                    <span class="val">{{ stats.totalOrders || 0 }} 单</span>
                  </li>
                </ul>
              </el-card>
            </el-col>
          </el-row>

          <!-- 个人信息表单 -->
          <el-card style="margin-top: 16px">
            <template #header>
              <span><el-icon><EditPen /></el-icon> 个人信息</span>
            </template>
            <el-form :model="profileForm" label-width="100px" size="large">
              <el-form-item label="用户名">
                <el-input v-model="profileForm.username" disabled />
              </el-form-item>
              <el-form-item label="昵称">
                <el-input v-model="profileForm.nickname" placeholder="请输入昵称" />
              </el-form-item>
              <el-form-item label="手机号">
                <el-input v-model="profileForm.phone" placeholder="请输入手机号" />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="saving" @click="saveProfile">
                  保存修改
                </el-button>
              </el-form-item>
            </el-form>
          </el-card>

          <!-- 修改密码 -->
          <el-card style="margin-top: 16px">
            <template #header>
              <span><el-icon><Lock /></el-icon> 修改密码</span>
            </template>
            <el-form :model="pwdForm" label-width="100px" size="large">
              <el-form-item label="当前密码">
                <el-input v-model="pwdForm.oldPassword" type="password" show-password />
              </el-form-item>
              <el-form-item label="新密码">
                <el-input v-model="pwdForm.newPassword" type="password" show-password />
              </el-form-item>
              <el-form-item label="确认密码">
                <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="pwdLoading" @click="changePassword">
                  修改密码
                </el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </el-col>
      </el-row>
    </div>
    <Footer />

    <!-- 地址管理弹窗 -->
    <el-dialog v-model="addrDialog.visible" title="管理收货地址" width="640px" @closed="resetAddrForm">
      <el-button type="primary" size="small" style="margin-bottom: 12px;" @click="openAddrEdit(null)">
        + 新增地址
      </el-button>
      <el-empty v-if="!addrDialog.list.length" description="还没有任何地址" :image-size="80" />
      <div v-else>
        <div v-for="a in addrDialog.list" :key="a.id" class="addr-item">
          <div class="addr-info">
            <div class="addr-line1">
              <span class="receiver">{{ a.receiver }}</span>
              <span class="phone">{{ a.phone }}</span>
              <el-tag v-if="a.isDefault" size="small" type="danger" effect="dark">默认</el-tag>
            </div>
            <div class="addr-line2">
              {{ a.province }} {{ a.city }} {{ a.district }} {{ a.detail }}
            </div>
          </div>
          <div class="addr-ops">
            <el-button v-if="!a.isDefault" size="small" type="primary" link @click="setDefaultAddr(a)">设为默认</el-button>
            <el-button size="small" link @click="openAddrEdit(a)">编辑</el-button>
            <el-button size="small" type="danger" link @click="deleteAddr(a)">删除</el-button>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 新增/编辑地址弹窗 -->
    <el-dialog v-model="addrEditDialog.visible" :title="addrEditDialog.form.id ? '编辑地址' : '新增地址'" width="500px" @closed="resetAddrForm">
      <el-form :model="addrEditDialog.form" label-width="90px">
        <el-form-item label="收货人" required>
          <el-input v-model="addrEditDialog.form.receiver" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="addrEditDialog.form.phone" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="省/市/区" required>
          <el-input v-model="addrEditDialog.form.province" placeholder="省" style="width:30%; margin-right:3%" />
          <el-input v-model="addrEditDialog.form.city" placeholder="市" style="width:30%; margin-right:3%" />
          <el-input v-model="addrEditDialog.form.district" placeholder="区/县" style="width:30%" />
        </el-form-item>
        <el-form-item label="详细地址" required>
          <el-input v-model="addrEditDialog.form.detail" type="textarea" :rows="2" placeholder="街道、楼栋、门牌号" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="addrEditDialog.form.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addrEditDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="addrEditDialog.loading" @click="submitAddr">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Navbar from '@/components/Navbar.vue'
import Footer from '@/components/Footer.vue'
import { useUserStore } from '@/stores/user'
import { updateUserInfo, updatePassword, getUserStats } from '@/api/user'
import { getAddressList, addAddress, updateAddress, deleteAddress, setDefaultAddress } from '@/api/address'

const userStore = useUserStore()
const saving = ref(false)
const pwdLoading = ref(false)
const stats = reactive({
  user: null,
  totalOrders: 0,
  pendingOrders: 0,
  paidOrders: 0,
  shippedOrders: 0,
  finishedOrders: 0,
  totalSpent: 0,
  favCount: 0,
  reviewCount: 0,
  couponCount: 0,
  defaultAddress: null,
  profileComplete: 0,
  profileTotal: 4,
  roleLabel: '普通用户',
  statusLabel: '正常'
})

const profileForm = reactive({
  username: '',
  nickname: '',
  phone: '',
  email: ''
})

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 资料完整度
const hasPhone = computed(() => !!stats.user?.phone)
const hasEmail = computed(() => !!stats.user?.email)
const hasAvatar = computed(() => !!stats.user?.avatar)
const hasDefaultAddress = computed(() => !!stats.defaultAddress)
const progressColor = computed(() => {
  const p = stats.profileComplete / stats.profileTotal
  if (p >= 0.75) return '#67c23a'
  if (p >= 0.5) return '#409eff'
  if (p >= 0.25) return '#e6a23c'
  return '#f56c6c'
})

function formatDate(t) {
  if (!t) return ''
  try {
    const d = new Date(t)
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const h = String(d.getHours()).padStart(2, '0')
    const min = String(d.getMinutes()).padStart(2, '0')
    return `${y}-${m}-${day} ${h}:${min}`
  } catch (e) {
    return ''
  }
}

function formatMoney(v) {
  if (v == null) return '0.00'
  return Number(v).toFixed(2)
}

async function loadStats() {
  try {
    const res = await getUserStats()
    if (res.data) {
      Object.assign(stats, res.data)
    }
  } catch (e) {
    console.error('加载用户统计失败', e)
  }
}

function loadProfile() {
  const user = userStore.userInfo
  if (user) {
    profileForm.username = user.username || ''
    profileForm.nickname = user.nickname || ''
    profileForm.phone = user.phone || ''
    profileForm.email = user.email || ''
  }
}

async function saveProfile() {
  saving.value = true
  try {
    const res = await updateUserInfo({
      nickname: profileForm.nickname,
      phone: profileForm.phone,
      email: profileForm.email
    })
    if (res.data) {
      userStore.setUserInfo(res.data)
    }
    ElMessage.success('保存成功')
    await loadStats()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function changePassword() {
  if (!pwdForm.oldPassword) {
    ElMessage.warning('请输入当前密码')
    return
  }
  if (!pwdForm.newPassword) {
    ElMessage.warning('请输入新密码')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning('两次密码输入不一致')
    return
  }
  if (pwdForm.newPassword.length < 6) {
    ElMessage.warning('密码至少6位')
    return
  }
  if (pwdForm.oldPassword === pwdForm.newPassword) {
    ElMessage.warning('新密码不能与当前密码相同')
    return
  }
  pwdLoading.value = true
  try {
    await updatePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码修改成功')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
  } catch (e) {
    const msg = e?.response?.data?.msg || e?.message || '密码修改失败'
    ElMessage.error(msg)
  } finally {
    pwdLoading.value = false
  }
}

// ============ 地址管理 ============
const addrDialog = reactive({ visible: false, list: [] })
const addrEditDialog = reactive({
  visible: false,
  loading: false,
  form: { id: null, receiver: '', phone: '', province: '', city: '', district: '', detail: '', isDefault: false }
})

function resetAddrForm() {
  addrEditDialog.form = { id: null, receiver: '', phone: '', province: '', city: '', district: '', detail: '', isDefault: false }
}

async function openAddressDialog() {
  addrDialog.visible = true
  try {
    const res = await getAddressList()
    addrDialog.list = Array.isArray(res.data) ? res.data : []
  } catch (e) {
    addrDialog.list = []
  }
}

function openAddrEdit(addr) {
  if (addr) {
    addrEditDialog.form = { ...addr }
  } else {
    resetAddrForm()
  }
  addrEditDialog.visible = true
}

async function submitAddr() {
  const f = addrEditDialog.form
  if (!f.receiver || !f.phone || !f.province || !f.city || !f.district || !f.detail) {
    ElMessage.warning('请完整填写地址信息')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(f.phone)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  addrEditDialog.loading = true
  try {
    const payload = { ...f, isDefault: f.isDefault ? 1 : 0 }
    if (f.id) {
      await updateAddress(payload)
      ElMessage.success('地址已更新')
    } else {
      await addAddress(payload)
      ElMessage.success('地址已添加')
    }
    addrEditDialog.visible = false
    await openAddressDialog()
    await loadStats()
  } catch (e) {
    const msg = e?.response?.data?.msg || e?.message || '保存失败'
    ElMessage.error(msg)
  } finally {
    addrEditDialog.loading = false
  }
}

async function setDefaultAddr(addr) {
  try {
    await setDefaultAddress({ id: addr.id })
    ElMessage.success('已设为默认地址')
    await openAddressDialog()
    await loadStats()
  } catch (e) {
    const msg = e?.response?.data?.msg || e?.message || '设置失败'
    ElMessage.error(msg)
  }
}

async function deleteAddr(addr) {
  try {
    await ElMessageBox.confirm(`确定要删除「${addr.receiver}」的地址吗？`, '删除确认', { type: 'warning' })
  } catch { return }
  try {
    await deleteAddress(addr.id)
    ElMessage.success('已删除')
    await openAddressDialog()
    await loadStats()
  } catch (e) {
    const msg = e?.response?.data?.msg || e?.message || '删除失败'
    ElMessage.error(msg)
  }
}

onMounted(() => {
  loadProfile()
  loadStats()
})
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf3 100%);
}
.main-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}
.page-title {
  font-size: 22px;
  margin: 0 0 20px;
  color: #1f2937;
}
/* 左侧用户卡 */
.profile-card {
  background: #fff;
  border-radius: 12px;
  padding: 28px 20px 16px;
  text-align: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  margin-bottom: 16px;
}
.avatar-section h3 {
  margin: 12px 0 4px;
  font-size: 18px;
  color: #1f2937;
}
.user-id {
  font-size: 12px;
  color: #909399;
  margin: 0 0 8px;
}
.badges {
  display: flex;
  justify-content: center;
  gap: 6px;
  margin-top: 6px;
}
.profile-stats {
  display: flex;
  justify-content: space-around;
  padding: 4px 0;
}
.stat-item {
  cursor: pointer;
  padding: 6px 4px;
  border-radius: 6px;
  transition: background 0.2s;
}
.stat-item:hover {
  background: #f0f5ff;
}
.stat-num {
  display: block;
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
}
.stat-label {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
/* 账户信息卡 */
.info-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}
.info-card-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 10px;
  border-left: 3px solid #6366f1;
  padding-left: 8px;
}
.info-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.info-list li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  font-size: 14px;
  border-bottom: 1px dashed #f0f0f0;
}
.info-list li:last-child {
  border-bottom: none;
}
.info-list.compact li {
  padding: 6px 0;
}
.info-list .lbl {
  color: #909399;
}
.info-list .val {
  color: #1f2937;
  font-weight: 500;
}
.info-list .val.ok {
  color: #67c23a;
}
.info-list .val.off {
  color: #f56c6c;
}
/* 资料完整度 */
.complete-bar {
  margin: 4px 0 16px;
}
.complete-tips {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 12px;
}
.tip {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #909399;
}
.tip.done {
  color: #67c23a;
}
.tip .el-icon {
  font-size: 16px;
}
/* 消费统计 */
.spend-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 18px;
}
.spend-item {
  text-align: center;
  padding: 10px 0;
  background: linear-gradient(135deg, #f0f5ff 0%, #e0eaff 100%);
  border-radius: 8px;
}
.spend-item .num {
  font-size: 22px;
  font-weight: 700;
  color: #4f46e5;
  line-height: 1.2;
}
.spend-item .lbl {
  font-size: 12px;
  color: #6b7280;
  margin-top: 2px;
}
.card-header { display: flex; justify-content: space-between; align-items: center; }
.addr-item { display: flex; justify-content: space-between; align-items: center; padding: 12px; margin-bottom: 8px; border: 1px solid #ebeef5; border-radius: 6px; transition: background 0.2s; }
.addr-item:hover { background: #f5f7fa; }
.addr-info { flex: 1; min-width: 0; }
.addr-line1 { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.addr-line1 .receiver { font-weight: 600; color: #303133; }
.addr-line1 .phone { color: #606266; font-size: 13px; }
.addr-line2 { color: #909399; font-size: 13px; line-height: 1.4; }
.addr-ops { display: flex; gap: 4px; flex-shrink: 0; }
/* 默认地址 */
.address-box {
  padding: 4px 0;
}
.addr-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.receiver {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}
.phone {
  font-size: 14px;
  color: #6b7280;
}
.addr-detail {
  font-size: 13px;
  color: #4b5563;
  line-height: 1.5;
}
.empty {
  text-align: center;
  padding: 14px 0;
  color: #909399;
}
.empty .el-icon {
  font-size: 36px;
  color: #c0c4cc;
  margin-bottom: 6px;
}
.empty p {
  margin: 0 0 10px;
  font-size: 13px;
}
/* 评价统计 */
.review-stat {
  text-align: center;
  padding: 6px 0;
}
.review-stat .big-num {
  font-size: 32px;
  font-weight: 700;
  color: #4f46e5;
  line-height: 1.1;
}
.review-stat .lbl {
  font-size: 13px;
  color: #6b7280;
  margin-top: 2px;
}
</style>
