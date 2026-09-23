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
        <el-dialog v-model="dialogVisible" :title="editingId ? '编辑商品' : '新增商品'" width="880px" :close-on-click-modal="false">
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
              <el-input-number v-model="form.price" :min="0.01" :precision="2" style="width: 100%" :disabled="multiSpec" />
              <span v-if="multiSpec" class="field-hint">多规格商品价格自动取各规格最低价</span>
            </el-form-item>
            <el-form-item label="原价">
              <el-input-number v-model="form.originalPrice" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
            <el-form-item label="库存" prop="stock">
              <el-input-number v-model="form.stock" :min="0" style="width: 100%" :disabled="multiSpec" />
              <span v-if="multiSpec" class="field-hint">多规格商品库存自动汇总各规格库存</span>
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

            <!-- ===== 多规格 SKU 配置 ===== -->
            <el-form-item label="多规格">
              <el-switch v-model="multiSpec" @change="onToggleMultiSpec" />
              <span class="field-hint">开启后可为商品设置不同规格（如颜色、容量），每个规格独立定价和库存</span>
            </el-form-item>

            <div v-if="multiSpec" class="sku-editor">
              <!-- 规格定义 -->
              <div class="spec-groups">
                <div v-for="(group, gi) in specGroups" :key="gi" class="spec-group-row">
                  <el-input v-model="group.name" placeholder="规格名（如：颜色）" style="width: 150px" />
                  <div class="spec-values">
                    <el-tag
                      v-for="(val, vi) in group.values"
                      :key="vi"
                      closable
                      class="spec-value-tag"
                      @close="removeSpecValue(gi, vi)"
                    >{{ val }}</el-tag>
                    <el-input
                      v-if="group.inputVisible"
                      ref="specValueInputs"
                      v-model="group.inputValue"
                      size="small"
                      style="width: 90px"
                      @keyup.enter="addSpecValue(gi)"
                      @blur="addSpecValue(gi)"
                    />
                    <el-button v-else size="small" plain @click="showSpecInput(gi)">+ 添加值</el-button>
                  </div>
                  <el-button link type="danger" @click="removeSpecGroup(gi)">删除规格</el-button>
                </div>
                <el-button size="small" plain @click="addSpecGroup" :disabled="specGroups.length >= 3">+ 添加规格（最多3个）</el-button>
              </div>

              <!-- SKU 明细表格 -->
              <div v-if="skuRows.length" class="sku-table-wrap">
                <div class="sku-batch-bar">
                  <span class="sku-count">共 {{ skuRows.length }} 个规格</span>
                  <el-button size="small" @click="batchFill('price')">批量设置价格</el-button>
                  <el-button size="small" @click="batchFill('stock')">批量设置库存</el-button>
                </div>
                <el-table :data="skuRows" border size="small">
                  <el-table-column label="规格组合" min-width="150">
                    <template #default="{ row }">{{ row.specKey }}</template>
                  </el-table-column>
                  <el-table-column label="价格（元）" width="130">
                    <template #default="{ row }">
                      <el-input-number v-model="row.price" :min="0.01" :precision="2" :controls="false" size="small" style="width: 110px" />
                    </template>
                  </el-table-column>
                  <el-table-column label="库存" width="110">
                    <template #default="{ row }">
                      <el-input-number v-model="row.stock" :min="0" :controls="false" size="small" style="width: 90px" />
                    </template>
                  </el-table-column>
                  <el-table-column label="SKU编码" width="130">
                    <template #default="{ row }">
                      <el-input v-model="row.skuCode" size="small" placeholder="选填" />
                    </template>
                  </el-table-column>
                  <el-table-column label="规格图片" width="80">
                    <template #default="{ row }">
                      <el-upload
                        action="/api/upload/image"
                        name="file"
                        :headers="uploadHeaders"
                        :show-file-list="false"
                        :on-success="(res) => handleSkuImageSuccess(res, row)"
                      >
                        <img v-if="row.image" :src="row.image" class="sku-thumb" />
                        <div v-else class="sku-thumb sku-thumb-empty"><el-icon><Plus /></el-icon></div>
                      </el-upload>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>

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
import { ref, reactive, onMounted, computed, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import AdminSidebar from './components/AdminSidebar.vue'
import AdminHeader from './components/AdminHeader.vue'
import { getAdminProductList, createProduct, updateProduct, deleteProduct } from '@/api/admin'
import { getProductDetail } from '@/api/product'
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

// ===== 多规格 SKU 状态 =====
const multiSpec = ref(false)
// 规格定义：[{ name: '颜色', values: ['黑','白'], inputVisible: false, inputValue: '' }]
const specGroups = ref([])
// SKU 明细行：[{ specs: {'颜色':'黑'}, specKey: '黑', price, stock, skuCode, image }]
const skuRows = ref([])
// 回显时暂停 watch 自动重建
const suspendRebuild = ref(false)

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
  multiSpec.value = false
  specGroups.value = []
  skuRows.value = []
}

/** 根据规格定义做笛卡尔积生成 SKU 行，已填过的价格/库存等按组合保留 */
function rebuildSkuRows() {
  const validGroups = specGroups.value
    .filter(g => g.name && g.name.trim() && g.values.length > 0)
  if (validGroups.length === 0) {
    skuRows.value = []
    return
  }
  let combos = [[]]
  for (const g of validGroups) {
    const next = []
    for (const prefix of combos) {
      for (const v of g.values) next.push([...prefix, { key: g.name.trim(), value: v }])
    }
    combos = next
  }
  const oldMap = new Map(skuRows.value.map(r => [r.specKey, r]))
  skuRows.value = combos.map(combo => {
    const specs = {}
    combo.forEach(c => { specs[c.key] = c.value })
    const specKey = combo.map(c => c.value).join(' / ')
    const old = oldMap.get(specKey)
    return old
      ? { ...old, specs }
      : { specs, specKey, price: 0, stock: 0, skuCode: '', image: '' }
  })
}

watch(specGroups, () => {
  if (!suspendRebuild.value) rebuildSkuRows()
}, { deep: true })

function onToggleMultiSpec(val) {
  if (val) {
    if (specGroups.value.length === 0) {
      specGroups.value = [makeEmptyGroup()]
    }
  } else {
    skuRows.value = []
  }
}

function makeEmptyGroup() {
  return { name: '', values: [], inputVisible: false, inputValue: '' }
}

function addSpecGroup() {
  if (specGroups.value.length >= 3) return
  specGroups.value.push(makeEmptyGroup())
}

function removeSpecGroup(gi) {
  specGroups.value.splice(gi, 1)
}

function showSpecInput(gi) {
  specGroups.value[gi].inputVisible = true
  nextTick(() => {
    const inputs = document.querySelectorAll('.spec-values .el-input__inner')
    if (inputs.length) inputs[inputs.length - 1].focus()
  })
}

function addSpecValue(gi) {
  const g = specGroups.value[gi]
  const val = (g.inputValue || '').trim()
  if (val && !g.values.includes(val)) g.values.push(val)
  g.inputValue = ''
  g.inputVisible = false
}

function removeSpecValue(gi, vi) {
  specGroups.value[gi].values.splice(vi, 1)
}

async function batchFill(field) {
  if (!skuRows.value.length) return
  try {
    const { value } = await ElMessageBox.prompt(
      field === 'price' ? '给所有规格设置统一价格（元）' : '给所有规格设置统一库存',
      '批量设置',
      { inputPattern: /^\d+(\.\d{1,2})?$/, inputErrorMessage: '请输入有效数字' }
    )
    const num = Number(value)
    skuRows.value.forEach(r => { r[field] = num })
  } catch {}
}

function handleSkuImageSuccess(res, row) {
  if (res && res.code === 200 && res.data) {
    row.image = res.data
    ElMessage.success('规格图片上传成功')
  } else {
    ElMessage.error(res?.msg || res?.message || '上传失败')
  }
}

async function openDialog(product) {
  resetForm()
  if (product) {
    editingId.value = product.id
    // 列表行没有 SKU 数据，调详情接口拿完整信息
    try {
      const res = await getProductDetail(product.id)
      const p = res.data || {}
      form.name = p.name
      form.description = p.description || ''
      form.categoryId = p.categoryId
      form.price = p.price
      form.originalPrice = p.originalPrice || 0
      form.stock = p.stock
      form.image = p.image || ''
      form.status = p.status ?? 1

      if (Array.isArray(p.skus) && p.skus.length > 0) {
        multiSpec.value = true
        suspendRebuild.value = true
        const keys = p.specKeys && p.specKeys.length
          ? p.specKeys
          : Array.from(new Set(p.skus.flatMap(s => Object.keys(s.specMap || {}))))
        specGroups.value = keys.map(k => ({
          name: k,
          values: (p.specOptions && p.specOptions[k]) || dedupeValues(p.skus, k),
          inputVisible: false,
          inputValue: ''
        }))
        skuRows.value = p.skus.map(s => {
          const map = s.specMap || safeParse(s.specs)
          return {
            specs: map,
            specKey: keys.map(k => map[k]).join(' / '),
            price: Number(s.price),
            stock: s.stock,
            skuCode: s.skuCode || '',
            image: s.image || ''
          }
        })
        await nextTick()
        suspendRebuild.value = false
      }
    } catch {
      // 详情失败时退回列表行数据
      Object.assign(form, {
        name: product.name, description: product.description || '',
        categoryId: product.categoryId, price: product.price,
        originalPrice: product.originalPrice || 0, stock: product.stock,
        image: product.image || '', status: product.status ?? 1
      })
    }
  }
  dialogVisible.value = true
}

function dedupeValues(skus, key) {
  const vals = []
  skus.forEach(s => {
    const map = s.specMap || safeParse(s.specs)
    if (map && map[key] && !vals.includes(map[key])) vals.push(map[key])
  })
  return vals
}

function safeParse(s) {
  try { return JSON.parse(s) } catch { return {} }
}

async function saveProduct() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  let skus = null
  if (multiSpec.value) {
    // 校验规格定义
    const validGroups = specGroups.value.filter(g => g.name && g.name.trim() && g.values.length > 0)
    if (validGroups.length === 0) {
      ElMessage.warning('请至少填写一个规格名及其规格值')
      return
    }
    for (const g of specGroups.value) {
      if (g.name && g.name.trim() && g.values.length === 0) {
        ElMessage.warning(`规格「${g.name}」至少需要一个规格值`)
        return
      }
    }
    // 校验每个 SKU
    for (const row of skuRows.value) {
      if (!row.price || row.price <= 0) {
        ElMessage.warning(`规格「${row.specKey}」的价格必须大于 0`)
        return
      }
      if (row.stock === null || row.stock === undefined || row.stock < 0) {
        ElMessage.warning(`规格「${row.specKey}」的库存不合法`)
        return
      }
    }
    skus = skuRows.value.map(r => ({
      skuCode: r.skuCode || null,
      specs: JSON.stringify(r.specs),
      price: r.price,
      stock: r.stock,
      image: r.image || null
    }))
  } else {
    // 关闭多规格：传空数组清空该商品已有 SKU
    skus = []
  }

  saving.value = true
  try {
    const data = { ...form, skus }
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
    const res = await getAdminProductList({ page: page.value, pageSize: pageSize.value, keyword: searchKeyword.value, sortBy: 'id' })
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
.field-hint { font-size: 12px; color: #909399; margin-left: 10px; }

/* ===== 多规格编辑器 ===== */
.sku-editor {
  width: 100%;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 14px;
  margin-bottom: 18px;
  background: #fafbfc;
}
.spec-groups { display: flex; flex-direction: column; gap: 10px; margin-bottom: 12px; }
.spec-group-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.spec-values {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  min-height: 32px;
  padding: 4px 8px;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  background: #fff;
}
.spec-value-tag { margin: 0; }
.sku-table-wrap { margin-top: 12px; }
.sku-batch-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.sku-count { font-size: 13px; color: #606266; margin-right: auto; }
.sku-thumb {
  width: 44px;
  height: 44px;
  border-radius: 4px;
  object-fit: cover;
  display: block;
  cursor: pointer;
}
.sku-thumb-empty {
  border: 1px dashed #d9d9d9;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8c939d;
  font-size: 16px;
}
.sku-thumb-empty:hover { border-color: #409eff; color: #409eff; }
</style>