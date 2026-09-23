import request from '@/utils/request'

export function getProductReviews(productId, params) {
  return request({
    url: '/review/product/' + productId,
    method: 'get',
    params
  })
}

export function addReview(data) {
  return request({
    url: '/review/create',
    method: 'post',
    data
  })
}

export function getMyReviews() {
  return request({
    url: '/review/user',
    method: 'get'
  })
}
export function updateReview(id, data) {
  return request({
    url: '/review/update/' + id,
    method: 'put',
    data
  })
}

export function deleteReview(id) {
  return request({
    url: '/review/delete/' + id,
    method: 'delete'
  })
}

export function getMyReviewForProduct(productId) {
  return request({
    url: '/review/check',
    method: 'get',
    params: { productId }
  })
}

export function checkCanReviewProduct(productId) {
  return request({
    url: '/review/check-can-review',
    method: 'get',
    params: { productId }
  })
}