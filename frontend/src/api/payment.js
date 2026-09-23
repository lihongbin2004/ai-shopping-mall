// 支付接口 - 后端暂无对应Controller，保留但注释说明
// 如后端未实现支付模块，前端可暂时不使用这些接口
import request from '@/utils/request'

export function createPayment(data) {
  return request({
    url: '/payment/create',
    method: 'post',
    data
  })
}

export function getPaymentStatus(orderNo) {
  return request({
    url: '/payment/status/' + orderNo,
    method: 'get'
  })
}

export function alipayPay(data) {
  return request({
    url: '/payment/alipay',
    method: 'post',
    data
  })
}

export function wechatPay(data) {
  return request({
    url: '/payment/wechat',
    method: 'post',
    data
  })
}

export function refundPay(data) {
  return request({
    url: '/payment/refund',
    method: 'post',
    data
  })
}

export function getPaymentMethods() {
  return request({
    url: '/payment/methods',
    method: 'get'
  })
}

export function getPaymentHistory(params) {
  return request({
    url: '/payment/history',
    method: 'get',
    params
  })
}
