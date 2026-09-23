<template>
  <div class="favorite-page">
    <Navbar />
    <div class="main-content">
      <h2 class="page-title">我的收藏</h2>

      <div v-loading="loading" class="favorite-grid">
        <el-row :gutter="20">
          <el-col v-for="item in favorites" :key="item.id" :xs="12" :sm="8" :md="6" :lg="6">
            <div class="favorite-card">
              <ProductCard :product="item" />
              <div class="card-actions">
                <el-button text type="danger" @click.stop="handleRemove(item)">
                  <el-icon><Delete /></el-icon> 取消收藏
                </el-button>
              </div>
            </div>
          </el-col>
        </el-row>
        <el-empty v-if="!favorites.length && !loading" description="暂无收藏" />
      </div>

      <div class="pagination-wrap" v-if="total > 0">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="fetchFavorites"
        />
      </div>
    </div>
    <Footer />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import Navbar from '@/components/Navbar.vue'
import Footer from '@/components/Footer.vue'
import ProductCard from '@/components/ProductCard.vue'
import { getFavoriteList, removeFavorite } from '@/api/favorite'

const favorites = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

async function fetchFavorites() {
  loading.value = true
  try {
    const res = await getFavoriteList({ page: page.value, pageSize: pageSize.value })
    favorites.value = res.data?.records || res.data?.list || res.data || []
    total.value = res.data?.total || 0
  } catch {} finally {
    loading.value = false
  }
}

async function handleRemove(item) {
  try {
    await removeFavorite(item.productId || item.id)
    ElMessage.success('已取消收藏')
    fetchFavorites()
  } catch {}
}

onMounted(fetchFavorites)
</script>

<style scoped>
.favorite-page { min-height: 100vh; background: #f5f7fa; }
.main-content { max-width: 1200px; margin: 0 auto; padding: 20px; }
.page-title { font-size: 22px; margin: 0 0 20px; }
.favorite-grid { min-height: 200px; }
.favorite-grid .el-col { margin-bottom: 20px; }
.favorite-card { position: relative; }
.card-actions { text-align: center; padding: 8px; background: #fafafa; border-radius: 0 0 8px 8px; }
.pagination-wrap { display: flex; justify-content: center; padding: 30px 0; }
</style>