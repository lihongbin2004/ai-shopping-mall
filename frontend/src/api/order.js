import request from '@/utils/request'

export function createOrder(data) {
  return request({
    url: '/order/create',
    method: 'post',
    data
  })
}

export function getOrderList(params) {
  return request({
    url: '/order/list',
    method: 'get',
    params
  })
}

export function getOrderDetail(id) {
  return request({
    url: '/order/detail/' + id,
    method: 'get'
  })
}

export function getOrderByOrderNo(orderNo) {
  return request({
    url: '/order/detail-by-no/' + orderNo,
    method: 'get'
  })
}

export function checkOrderPayStatus(id) {
  return request({
    url: '/payment/check/' + id,
    method: 'get'
  })
}

export function cancelOrder(id) {
  return request({
    url: '/order/cancel/' + id,
    method: 'post'
  })
}

export function payOrder(id, data = {}) {
  return request({
    url: '/order/pay/' + id,
    method: 'post',
    data
  })
}

export function confirmOrder(id) {
  return request({
    url: '/order/confirm/' + id,
    method: 'post'
  })
}

export function shipOrder(id) {
  return request({
    url: '/order/ship/' + id,
    method: 'post'
  })
}

export function deleteOrder(id) {
  return request({
    url: '/order/' + id,
    method: 'delete'
  })
}

// 支付宝沙箱支付：后端返回 form HTML，前端提交到支付宝网关
export function createAlipayPay(orderId) {
  return request({
    url: '/payment/alipay/create/' + orderId,
    method: 'post',
    timeout: 6000  // 沙箱网关经常 502，6s 没回就快速失败
  })
}

// 模拟支付（沙箱挂时调试用）
export function mockPay(orderId) {
  return request({
    url: '/payment/mock-pay/' + orderId,
    method: 'post'
  })
}

// 主动查询订单支付状态（支付完成返回后调用，用于同步本地状态）
export function queryPayStatus(orderId) {
  return request({
    url: '/payment/alipay/query/' + orderId,
    method: 'post'
  })
}

// 查询模拟物流轨迹
export function getLogisticsTrack(orderId) {
  return request({
    url: '/logistics/track/' + orderId,
    method: 'get'
  })
}