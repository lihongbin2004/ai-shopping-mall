import request from '@/utils/request'

export function getCartList() {
  return request({
    url: '/cart/list',
    method: 'get'
  })
}

export function addToCart(data) {
  return request({
    url: '/cart/add',
    method: 'post',
    data
  })
}

export function updateCartItem(id, data) {
  return request({
    url: '/cart/update',
    method: 'put',
    data: { id, ...data }
  })
}

export function removeCartItem(id) {
  return request({
    url: '/cart/delete/' + id,
    method: 'delete'
  })
}

export function clearCart() {
  return request({
    url: '/cart/clear',
    method: 'delete'
  })
}

export function toggleCartCheck(id, checked) {
  return request({
    url: '/cart/update',
    method: 'put',
    data: { id, checked }
  })
}

export function toggleAllCheck(checked) {
  return request({
    url: '/cart/check/all',
    method: 'put',
    data: { checked }
  })
}

export function getCartCount() {
  return request({
    url: '/cart/list',
    method: 'get'
  })
}