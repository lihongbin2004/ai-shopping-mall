<template>
  <div class="review-page">
    <Navbar />
    <div class="main-content">
      <h2 class="page-title">我的评价</h2>

      <div v-loading="loading" class="review-list">
        <div v-for="rv in reviews" :key="rv.id" class="review-card">
          <div class="review-product" @click="$router.push(`/product/${rv.productId}`)">
            <el-image :src="rv.productImage" class="product-img" />
            <span>{{ rv.productName }}</span>
          </div>
          <div class="review-content">
            <div class="review-header">
              <el-rate v-model="rv.rating" disabled :max="5" size="small" />
              <span class="review-time">{{ rv.createdAt }}</span>
            </div>
            <p>{{ rv.content }}</p>
            <div v-if="rv.images" class="review-images">
              <el-image v-for="(img, i) in rv.images" :key="i" :src="img" class="review-img" />
            </div>
          </div>
          <div class="review-actions">
            <el-button text size="small" @click="openEdit(rv)">编辑</el-button>
            <el-button text type="danger" size="small" @click="handleDelete(rv)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="!reviews.length && !loading" description="暂无评价" />
      </div>

      <!-- 编辑评价对话框 -->
      <el-dialog v-model="dialogVisible" title="编辑评价" width="500px">
        <el-form :model="editForm" label-width="60px">
          <el-form-item label="评分">
            <el-rate v-model="editForm.rating" :max="5" />
          </el-form-item>
          <el-form-item label="内容">
            <el-input v-model="editForm.content" type="textarea" :rows="4" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
        </template>
      </el-dialog>
    </div>
    <Footer />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Navbar from '@/components/Navbar.vue'
import Footer from '@/components/Footer.vue'
import { getMyReviews, updateReview, deleteReview } from '@/api/review'

const reviews = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)

const editForm = reactive({
  rating: 5,
  content: ''
})

function openEdit(rv) {
  editingId.value = rv.id
  editForm.rating = rv.rating
  editForm.content = rv.content
  dialogVisible.value = true
}

async function submitEdit() {
  saving.value = true
  try {
    await updateReview(editingId.value, { rating: editForm.rating, content: editForm.content })
    ElMessage.success('评价已更新')
    dialogVisible.value = false
    fetchReviews()
  } catch {} finally {
    saving.value = false
  }
}

async function handleDelete(rv) {
  try {
    await ElMessageBox.confirm('确定要删除该评价吗？')
    await deleteReview(rv.id)
    ElMessage.success('已删除')
    fetchReviews()
  } catch {}
}

async function fetchReviews() {
  loading.value = true
  try {
    const res = await getMyReviews()
    reviews.value = res.data || []
  } catch {} finally {
    loading.value = false
  }
}

onMounted(fetchReviews)
</script>

<style scoped>
.review-page { min-height: 100vh; background: #f5f7fa; }
.main-content { max-width: 1200px; margin: 0 auto; padding: 20px; }
.page-title { font-size: 22px; margin: 0 0 20px; }
.review-list { display: flex; flex-direction: column; gap: 12px; }
.review-card { background: #fff; border-radius: 8px; padding: 16px 20px; }
.review-product { display: flex; align-items: center; gap: 10px; padding-bottom: 10px; border-bottom: 1px solid #f0f0f0; margin-bottom: 10px; cursor: pointer; font-size: 14px; color: #409eff; }
.product-img { width: 40px; height: 40px; border-radius: 4px; }
.review-header { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.review-time { font-size: 12px; color: #c0c4cc; }
.review-content p { margin: 0; color: #606266; line-height: 1.6; }
.review-images { display: flex; gap: 8px; margin-top: 8px; }
.review-img { width: 80px; height: 80px; border-radius: 4px; }
.review-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 10px; }
</style>