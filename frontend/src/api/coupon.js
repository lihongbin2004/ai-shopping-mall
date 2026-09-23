import request from '@/utils/request'

export function getCouponList(params) {
  return request({
    url: '/coupon/list',
    method: 'get',
    params
  })
}

export function getUsableCoupons(params) {
  return request({
    url: '/coupon/usable',
    method: 'get',
    params
  })
}

export function receiveCoupon(couponId) {
  return request({
    url: '/coupon/receive/' + couponId,
    method: 'post'
  })
}

export function getMyCoupons() {
  return request({
    url: '/coupon/my',
    method: 'get'
  })
}

export function createCoupon(data) {
  return request({
    url: '/coupon/create',
    method: 'post',
    data
  })
}

export function updateCoupon(id, data) {
  return request({
    url: '/coupon/update',
    method: 'put',
    data: { ...data, id }
  })
}

export function deleteCoupon(id) {
  return request({
    url: '/coupon/delete/' + id,
    method: 'delete'
  })
}
export function addCoupon(data) {
  return request({
    url: '/coupon/create',
    method: 'post',
    data
  })
}

export function claimCoupon(couponId) {
  return request({
    url: '/coupon/receive/' + couponId,
    method: 'post'
  })
}

export function getAvailableCoupons(params) {
  return request({
    url: '/coupon/usable',
    method: 'get',
    params
  })
}