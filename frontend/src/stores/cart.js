import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getCartList, addToCart, updateCartItem, removeCartItem, clearCart, getCartCount } from '@/api/cart'
import { ElMessage } from 'element-plus'

export const useCartStore = defineStore('cart', () => {
  const cartItems = ref([])
  const count = ref(0)

  const totalPrice = computed(() => {
    return cartItems.value
      .filter(item => item.checked)
      .reduce((sum, item) => sum + Number(item.productPrice) * item.quantity, 0)
  })

  const checkedCount = computed(() => {
    return cartItems.value.filter(item => item.checked).length
  })

  const selectedIds = computed(() => {
    return cartItems.value.filter(item => item.checked).map(item => item.id)
  })

  async function fetchCartList() {
    try {
      const res = await getCartList()
      const list = (res.data || []).map(item => ({ ...item, checked: item.checked === 1 }))
      cartItems.value = list
      count.value = list.length
      return list
    } catch {
      return []
    }
  }

  async function fetchCartCount() {
    try {
      const res = await getCartCount()
      const list = res.data || []
      count.value = list.length
    } catch {
      // ignore
    }
  }

  async function addItem(data) {
    const res = await addToCart(data)
    await fetchCartCount()
    ElMessage.success('已添加到购物车')
    return res
  }

  async function updateItem(id, data) {
    const res = await updateCartItem(id, data)
    await fetchCartList()
    return res
  }

  async function removeItem(id) {
    await removeCartItem(id)
    await fetchCartList()
    ElMessage.success('已移除')
  }

  async function clearAll() {
    await clearCart()
    cartItems.value = []
    count.value = 0
    ElMessage.success('购物车已清空')
  }

  return {
    cartItems,
    count,
    totalPrice,
    checkedCount,
    selectedIds,
    fetchCartList,
    fetchCartCount,
    addItem,
    updateItem,
    removeItem,
    clearAll
  }
})