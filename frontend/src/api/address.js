import request from '@/utils/request'

export function getAddressList() {
  return request({
    url: '/address/list',
    method: 'get'
  })
}

export function addAddress(data) {
  return request({
    url: '/address/create',
    method: 'post',
    data
  })
}

export function updateAddress(data) {
  return request({
    url: '/address/update',
    method: 'put',
    data
  })
}

export function deleteAddress(id) {
  return request({
    url: '/address/delete/' + id,
    method: 'delete'
  })
}

export function setDefaultAddress(data) {
  return request({
    url: '/address/setDefault',
    method: 'post',
    data
  })
}
