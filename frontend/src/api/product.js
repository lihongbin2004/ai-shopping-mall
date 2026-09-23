import request from '@/utils/request'

export function getProductList(params) {
  return request({
    url: '/product/page',
    method: 'get',
    params
  })
}

export function getProductDetail(id) {
  return request({
    url: '/product/detail/' + id,
    method: 'get'
  })
}

export function searchProduct(keyword) {
  return request({
    url: '/product/search',
    method: 'get',
    params: { keyword: keyword }
  })
}

export function getProductByCategory(categoryId) {
  return request({
    url: '/product/category/' + categoryId,
    method: 'get'
  })
}

export function createProduct(data) {
  return request({
    url: '/product/create',
    method: 'post',
    data
  })
}

export function updateProduct(data) {
  return request({
    url: '/product/update',
    method: 'put',
    data
  })
}

export function deleteProduct(id) {
  return request({
    url: '/product/delete/' + id,
    method: 'delete'
  })
}

export function getHotProducts() {
  return request({
    url: '/product/page',
    method: 'get',
    params: { page: 1, size: 4 }
  })
}

export function getNewProducts() {
  return request({
    url: '/product/page',
    method: 'get',
    params: { page: 1, size: 4 }
  })
}

export function getProductRecommend() {
  // 调用真正的推荐接口（含兴趣画像/分类召回/排序/Redis 缓存）
  const userId = getUserId()
  return request({
    url: '/recommend/for-you',
    method: 'get',
    params: { userId, limit: 8 }
  })
}

function getUserId() {
  try {
    const raw = localStorage.getItem('userInfo') || localStorage.getItem('user')
    if (!raw) return null
    const u = JSON.parse(raw)
    return u?.id || u?.userId || null
  } catch (e) { return null }
}