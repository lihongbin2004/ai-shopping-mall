import axios from 'axios'
import { getToken } from '@/utils/auth'

const uploadRequest = axios.create({
  baseURL: '/api',
  timeout: 30000
})

uploadRequest.interceptors.request.use(config => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

uploadRequest.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      return Promise.reject(new Error(res.msg || res.message || '上传失败'))
    }
    return res
  },
  error => {
    return Promise.reject(error)
  }
)

export function uploadImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  return uploadRequest({
    url: '/upload/image',
    method: 'post',
    data: formData
  })
}
