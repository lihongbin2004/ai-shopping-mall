import request from '@/utils/request'

export function createRefund(data) {
  return request({
    url: '/refund/create',
    method: 'post',
    data
  })
}

export function getMyRefunds() {
  return request({
    url: '/refund/list',
    method: 'get'
  })
}

export function getRefundDetail(id) {
  return request({
    url: '/refund/detail/' + id,
    method: 'get'
  })
}

export function getAdminRefunds(params) {
  return request({
    url: '/refund/admin/list',
    method: 'get',
    params
  })
}

export function auditRefund(id, data) {
  return request({
    url: '/refund/admin/audit/' + id,
    method: 'post',
    data
  })
}
