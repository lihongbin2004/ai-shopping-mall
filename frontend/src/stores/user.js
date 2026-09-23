import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getToken, setToken, removeToken, getUser, setUser, removeUser } from '@/utils/auth'
import { login as loginApi, getUserInfo, register as registerApi } from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const userInfo = ref(getUser())

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 'ADMIN')
  const nickname = computed(() => userInfo.value?.nickname || userInfo.value?.username || '')
  const avatar = computed(() => userInfo.value?.avatar || '')

  async function login(loginData) {
    const res = await loginApi(loginData)
    const data = res.data
    token.value = data.token
    setToken(data.token)
    if (data.user) {
      userInfo.value = data.user
      setUser(data.user)
    }
    return data
  }

  async function register(registerData) {
    const res = await registerApi(registerData)
    return res.data
  }

  async function fetchUserInfo() {
    try {
      const res = await getUserInfo()
      userInfo.value = res.data
      setUser(res.data)
      return res.data
    } catch {
      return null
    }
  }

  function setUserInfo(user) {
    userInfo.value = user
    setUser(user)
  }

  function logout() {
    token.value = null
    userInfo.value = null
    removeToken()
    removeUser()
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    isAdmin,
    nickname,
    avatar,
    login,
    register,
    fetchUserInfo,
    setUserInfo,
    logout
  }
})
