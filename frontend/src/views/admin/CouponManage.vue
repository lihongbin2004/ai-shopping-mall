<template>
  <div class="admin-layout">
    <AdminSidebar />
    <div class="admin-main">
      <AdminHeader title="优惠券管理" />
      <div class="admin-content">
        <el-card>
          <div class="toolbar">
            <el-button type="success" @click="openDialog()">新增优惠券</el-button>
          </div>
          <el-table :data="coupons" v-loading="loading" stripe border>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column label="优惠" width="140">
              <template #default="{ row }">满{{ row.minAmount }}减¥{{ row.discount }}</template>
            </el-table-column>
            <el-table-column prop="stock" label="库存" width="70" />
            <el-table-column prop="usedCount" label="已领" width="70" />
            <el-table-column prop="startTime" label="开始时间" width="160" />
            <el-table-column prop="endTime" label="结束时间" width="160" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openDialog(row)">编辑</el-button>
                <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="page"
              :page-size="pageSize"
              :total="total"
              layout="total, prev, pager, next"
              @current-change="fetchCoupons"
            />
          </div>
        </el-card>

        <el-dialog v-model="dialogVisible" :title="editingId ? '编辑优惠券' : '新增优惠券'" width="500px">
          <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
            <el-form-item label="名称" prop="name">
              <el-input v-model="form.name" />
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="form.description" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="最低消费" prop="minAmount">
              <el-input-number v-model="form.minAmount" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
            <el-form-item label="减免金额" prop="discount">
              <el-input-number v-model="form.discount" :min="0.01" :precision="2" style="width: 100%" />
            </el-form-item>
            <el-form-item label="库存" prop="stock">
              <el-input-number v-model="form.stock" :min="0" style="width: 100%" />
            </el-form-item>
            <el-form-item label="开始时间" prop="startTime">
              <el-date-picker v-model="form.startTime" type="datetime" style="width: 100%" />
            </el-form-item>
            <el-form-item label="结束时间" prop="endTime">
              <el-date-picker v-model="form.endTime" type="datetime" style="width: 100%" />
            </el-form-item>
            <el-form-item label="启用">
              <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="saveCoupon">保存</el-button>
          </template>
        </el-dialog>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminSidebar from './components/AdminSidebar.vue'
import AdminHeader from './components/AdminHeader.vue'
import { getCouponList, addCoupon, updateCoupon, deleteCoupon } from '@/api/coupon'

const coupons = ref([])
const loading = ref(false)
const saving = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref(null)

const form = reactive({
  name: '',
  description: '',
  minAmount: 0,
  discount: 0,
  stock: 100,
  startTime: '',
  endTime: '',
  status: 1
})

const rules = {
  name: [{ required: true, message: '请输入名称' }],
  minAmount: [{ required: true, message: '请输入最低消费' }],
  discount: [{ required: true, message: '请输入减免金额' }]
}

function resetForm() {
  form.name = ''
  form.description = ''
  form.minAmount = 0
  form.discount = 0
  form.stock = 100
  form.startTime = ''
  form.endTime = ''
  form.status = 1
  editingId.value = null
}

function openDialog(coupon) {
  resetForm()
  if (coupon) {
    editingId.value = coupon.id
    form.name = coupon.name
    form.description = coupon.description || ''
    form.minAmount = coupon.minAmount
    form.discount = coupon.discount
    form.stock = coupon.stock
    form.startTime = coupon.startTime
    form.endTime = coupon.endTime
    form.status = coupon.status ?? 1
  }
  dialogVisible.value = true
}

async function saveCoupon() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const data = { ...form }
    if (editingId.value) {
      await updateCoupon(editingId.value, data)
      ElMessage.success('优惠券已更新')
    } else {
      await addCoupon(data)
      ElMessage.success('优惠券已创建')
    }
    dialogVisible.value = false
    fetchCoupons()
  } catch {} finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定要删除该优惠券吗？')
    await deleteCoupon(row.id)
    ElMessage.success('已删除')
    fetchCoupons()
  } catch {}
}

async function fetchCoupons() {
  loading.value = true
  try {
    const res = await getCouponList({ page: page.value, pageSize: pageSize.value })
    coupons.value = res.data?.records || res.data?.list || res.data || []
    total.value = res.data?.total || 0
  } catch {} finally {
    loading.value = false
  }
}

onMounted(fetchCoupons)
</script>

<style scoped>
.admin-layout { display: flex; min-height: 100vh; background: #f0f2f5; }
.admin-main { flex: 1; display: flex; flex-direction: column; }
.admin-content { padding: 20px; flex: 1; }
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; }
.pagination-wrap { display: flex; justify-content: center; margin-top: 16px; }
</style>