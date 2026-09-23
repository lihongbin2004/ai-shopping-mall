<template>
  <div class="admin-layout">
    <AdminSidebar />
    <div class="admin-main">
      <AdminHeader title="商品管理" />
      <div class="admin-content">
        <el-card>
          <div class="toolbar">
            <el-input v-model="searchKeyword" placeholder="搜索商品名称" clearable style="width: 200px" />
            <el-button type="primary" @click="fetchProducts">搜索</el-button>
            <el-button type="success" @click="openDialog()">新增商品</el-button>
          </div>
          <el-table :data="products" v-loading="loading" stripe border>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column label="图片" width="70">
              <template #default="{ row }">
                <el-image :src="row.image" class="table-img" />
              </template>
            </el-table-column>
            <el-table-column prop="name" label="商品名称" min-width="180" />
            <el-table-column prop="categoryName" label="分类" width="100" />
            <el-table-column prop="price" label="价格" width="100">
              <template #default="{ row }">¥{{ row.price?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="stock" label="库存" width="80" />
            <el-table-column prop="sales" label="销量" width="80" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '上架' : '下架' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
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
              @current-change="fetchProducts"
            />
          </div>
        </el-card>

        <!-- 商品编辑对话框 -->
        <el-dialog v-model="dialogVisible" :title="editingId ? '编辑商品' : '新增商品'" width="600px">
          <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
            <el-form-item label="商品名称" prop="name">
              <el-input v-model="form.name" />
            </el-form-item>
            <el-form-item label="描述" prop="description">
              <el-input v-model="form.description" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="分类" prop="categoryId">
              <el-select v-model="form.categoryId" placeholder="选择分类" style="width: 100%">
                <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="价格" prop="price">
              <el-input-number v-model="form.price" :min="0.01" :precision="2" style="width: 100%" />
            </el-form-item>
            <el-form-item label="原价">
              <el-input-number v-model="form.originalPrice" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
            <el-form-item label="库存" prop="stock">
              <el-input-number v-model="form.stock" :min="0" style="width: 100%" />
            </el-form-item>
            <el-form-item label="商品图片">
              <el-upload
                class="image-uploader"
                action="/api/upload/image"
                name="file"
                :headers="uploadHeaders"
                :show-file-list="false"
                :before-upload="beforeImageUpload"
                :on-success="handleImageSuccess"
                :on-error="handleImageError"
              >
                <div v-if="form.image" class="image-preview">
                  <img :src="form.image" />
                  <div class="image-mask">
                    <el-icon @click.stop="form.image = ''"><Delete /></el-icon>
                  </div>
                </div>
                <el-icon v-else class="image-uploader-icon"><Plus /></el-icon>
              </el-upload>
              <div class="image-tip">点击上传，无格式与大小限制</div>
            </el-form-item>
            <el-form-item label="上架">
              <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="saveProduct">保存</el-button>
          </template>
        </el-dialog>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import AdminSidebar from './components/AdminSidebar.vue'
import AdminHeader from './components/AdminHeader.vue'
import { getAdminProductList, createProduct, updateProduct, deleteProduct } from '@/api/admin'
import { getCategoryList } from '@/api/category'
import { getToken } from '@/utils/auth'

const products = ref([])
const categories = ref([])
const loading = ref(false)
const saving = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const searchKeyword = ref('')
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref(null)

const uploadHeaders = computed(() => {
  const token = getToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
})

const form = reactive({
  name: '',
  description: '',
  categoryId: null,
  price: 0,
  originalPrice: 0,
  stock: 0,
  image: '',
  status: 1
})

const rules = {
  name: [{ required: true, message: '请输入商品名称' }],
  price: [{ required: true, message: '请输入价格' }],
  stock: [{ required: true, message: '请输入库存' }]
}

function resetForm() {
  form.name = ''
  form.description = ''
  form.categoryId = null
  form.price = 0
  form.originalPrice = 0
  form.stock = 0
  form.image = ''
  form.status = 1
  editingId.value = null
}

function openDialog(product) {
  resetForm()
  if (product) {
    editingId.value = product.id
    form.name = product.name
    form.description = product.description || ''
    form.categoryId = product.categoryId
    form.price = product.price
    form.originalPrice = product.originalPrice || 0
    form.stock = product.stock
    form.image = product.image || ''
    form.status = product.status ?? 1
  }
  dialogVisible.value = true
}

async function saveProduct() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const data = { ...form }
    if (editingId.value) {
      await updateProduct(editingId.value, data)
      ElMessage.success('商品已更新')
    } else {
      await createProduct(data)
      ElMessage.success('商品已创建')
    }
    dialogVisible.value = false
    fetchProducts()
  } catch {} finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除商品"${row.name}"吗？`)
    await deleteProduct(row.id)
    ElMessage.success('已删除')
    fetchProducts()
  } catch {}
}

async function fetchProducts() {
  loading.value = true
  try {
    const res = await getAdminProductList({ page: page.value, pageSize: pageSize.value, keyword: searchKeyword.value })
    products.value = res.data?.records || res.data?.list || res.data || []
    total.value = res.data?.total || 0
  } catch {} finally {
    loading.value = false
  }
}

function beforeImageUpload() {
  return true
}

function handleImageSuccess(res) {
  if (res && res.code === 200 && res.data) {
    form.image = res.data
    ElMessage.success('图片上传成功')
  } else {
    ElMessage.error(res?.msg || res?.message || '上传失败')
  }
}

function handleImageError(err) {
  let msg = '上传失败'
  try {
    const parsed = JSON.parse(err.message)
    msg = parsed.msg || parsed.message || msg
  } catch {}
  ElMessage.error(msg)
}

onMounted(async () => {
  const catRes = await getCategoryList().catch(() => ({ data: [] }))
  categories.value = catRes.data || []
  fetchProducts()
})
</script>

<style scoped>
.admin-layout { display: flex; min-height: 100vh; background: #f0f2f5; }
.admin-main { flex: 1; display: flex; flex-direction: column; }
.admin-content { padding: 20px; flex: 1; }
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.table-img { width: 40px; height: 40px; border-radius: 4px; }
.pagination-wrap { display: flex; justify-content: center; margin-top: 16px; }
.image-uploader :deep(.el-upload) {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  width: 120px;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.2s;
}
.image-uploader :deep(.el-upload:hover) { border-color: #409eff; }
.image-uploader-icon {
  font-size: 28px;
  color: #8c939d;
}
.image-preview {
  width: 120px;
  height: 120px;
  position: relative;
}
.image-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.image-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
  cursor: pointer;
}
.image-mask .el-icon {
  color: #fff;
  font-size: 22px;
}
.image-mask .el-icon:hover { color: #f56c6c; }
.image-preview:hover .image-mask { opacity: 1; }
.image-tip { font-size: 12px; color: #999; margin-top: 6px; }
</style>