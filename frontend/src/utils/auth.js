const TokenKey = 'ai-mall-token'
const UserInfoKey = 'ai-mall-user'

export function getToken() {
  return localStorage.getItem(TokenKey)
}

export function setToken(token) {
  return localStorage.setItem(TokenKey, token)
}

export function removeToken() {
  return localStorage.removeItem(TokenKey)
}

export function getUser() {
  const str = localStorage.getItem(UserInfoKey)
  if (str) {
    try {
      return JSON.parse(str)
    } catch {
      return null
    }
  }
  return null
}

export function setUser(user) {
  return localStorage.setItem(UserInfoKey, JSON.stringify(user))
}

export function removeUser() {
  return localStorage.removeItem(UserInfoKey)
}
