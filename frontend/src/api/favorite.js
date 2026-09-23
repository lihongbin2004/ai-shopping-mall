import request from '@/utils/request'

export function getFavoriteList() {
  return request({
    url: '/favorite/list',
    method: 'get'
  })
}

export function addFavorite(productId) {
  return request({
    url: '/favorite/add/' + productId,
    method: 'post'
  })
}

export function removeFavorite(productId) {
  return request({
    url: '/favorite/cancel/' + productId,
    method: 'delete'
  })
}

export function checkFavorite(productId) {
  return request({
    url: '/favorite/check/' + productId,
    method: 'get'
  })
}
