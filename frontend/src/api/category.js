import request from '@/utils/request'

export function getCategoryList() {
  return request({
    url: '/category/list',
    method: 'get'
  })
}

export function getCategoryTree() {
  return request({
    url: '/category/tree',
    method: 'get'
  })
}

export function createCategory(data) {
  return request({
    url: '/category/create',
    method: 'post',
    data
  })
}

export function updateCategory(data) {
  return request({
    url: '/category/update',
    method: 'put',
    data
  })
}

export function deleteCategory(id) {
  return request({
    url: '/category/delete/' + id,
    method: 'delete'
  })
}
