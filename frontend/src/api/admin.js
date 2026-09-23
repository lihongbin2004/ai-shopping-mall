import request from '@/utils/request'

export function getDashboardStats() {
  return request({
    url: '/admin/stats',
    method: 'get'
  })
}

export function getAdminUserList(params) {
  return request({
    url: '/admin/users',
    method: 'get',
    params
  })
}

export function updateUserStatus(data) {
  return request({
    url: '/admin/user/status',
    method: 'post',
    data
  })
}

export function getAdminOrderList(params) {
  return request({
    url: '/admin/orders',
    method: 'get',
    params
  })
}

export function getAdminOrderDetail(id) {
  return request({
    url: `/admin/order/${id}`,
    method: 'get'
  })
}

export function updateOrderStatus(data) {
  return request({
    url: '/admin/order/status',
    method: 'post',
    data
  })
}
// Dashboard reports
export function getSalesReport(params) {
  return request({
    url: '/admin/sales/report',
    method: 'get',
    params
  })
}

export function getProductStats() {
  return request({
    url: '/admin/product/stats',
    method: 'get'
  })
}

export function getCategoryStats() {
  return request({
    url: '/admin/category/stats',
    method: 'get'
  })
}

// System config
export function getSystemConfig() {
  return request({
    url: '/admin/config',
    method: 'get'
  })
}

export function updateSystemConfig(data) {
  return request({
    url: '/admin/config',
    method: 'post',
    data
  })
}

// RAG 知识库管理（CRUD）
export function getKnowledgeList() {
  return request({ url: '/admin/knowledge/list', method: 'get' })
}
export function getKnowledgeDetail(id) {
  return request({ url: `/admin/knowledge/${id}`, method: 'get' })
}
export function createKnowledge(data) {
  return request({ url: '/admin/knowledge/create', method: 'post', data })
}
export function updateKnowledge(data) {
  return request({ url: '/admin/knowledge/update', method: 'post', data })
}
export function deleteKnowledge(id) {
  return request({ url: '/admin/knowledge/delete', method: 'post', data: { id } })
}
export function toggleKnowledge(data) {
  return request({ url: '/admin/knowledge/toggle', method: 'post', data })
}
// Product management (re-exported from product.js for convenience)
export function getAdminProductList(params) {
  return request({
    url: '/product/page',
    method: 'get',
    params
  })
}

export function createProduct(data) {
  return request({
    url: '/product/create',
    method: 'post',
    data
  })
}

export function updateProduct(id, data) {
  return request({
    url: '/product/update',
    method: 'put',
    data: { ...data, id }
  })
}

export function deleteProduct(id) {
  return request({
    url: '/product/delete/' + id,
    method: 'delete'
  })
}