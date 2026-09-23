import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'
import { ElMessage } from 'element-plus'

const routes = [
  {
    path: '/',
    redirect: '/home'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/front/Login.vue'),
    meta: { title: '登录', noAuth: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/front/Register.vue'),
    meta: { title: '注册', noAuth: true }
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('@/views/front/Home.vue'),
    meta: { title: '首页', noAuth: true }
  },
  {
    path: '/products',
    name: 'ProductList',
    component: () => import('@/views/front/ProductList.vue'),
    meta: { title: '商品列表', noAuth: true }
  },
  {
    path: '/product/:id',
    name: 'ProductDetail',
    component: () => import('@/views/front/ProductDetail.vue'),
    meta: { title: '商品详情', noAuth: true }
  },
  {
    path: '/cart',
    name: 'Cart',
    component: () => import('@/views/front/Cart.vue'),
    meta: { title: '购物车', requiresAuth: true }
  },
  {
    path: '/order/confirm',
    name: 'OrderConfirm',
    component: () => import('@/views/front/OrderConfirm.vue'),
    meta: { title: '确认订单', requiresAuth: true }
  },
  {
    path: '/orders',
    name: 'OrderList',
    component: () => import('@/views/front/OrderList.vue'),
    meta: { title: '我的订单', requiresAuth: true }
  },
  {
    path: '/order/:id',
    name: 'OrderDetail',
    component: () => import('@/views/front/OrderDetail.vue'),
    meta: { title: '订单详情', requiresAuth: true }
  },
  {
    path: '/reviews',
    name: 'Review',
    component: () => import('@/views/front/Review.vue'),
    meta: { title: '我的评价', requiresAuth: true }
  },
  {
    path: '/favorites',
    name: 'Favorite',
    component: () => import('@/views/front/Favorite.vue'),
    meta: { title: '我的收藏', requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/front/Profile.vue'),
    meta: { title: '个人中心', requiresAuth: true }
  },
  {
    path: '/coupon',
    name: 'CouponCenter',
    component: () => import('@/views/front/CouponCenter.vue'),
    meta: { title: '优惠券中心', requiresAuth: true }
  },
  {
    path: '/order/pay-success',
    name: 'PaySuccess',
    component: () => import('@/views/front/PaySuccess.vue'),
    meta: { title: '支付完成', requiresAuth: true }
  },
  {
    path: '/refunds',
    name: 'RefundList',
    component: () => import('@/views/front/RefundList.vue'),
    meta: { title: '我的退款', requiresAuth: true }
  },
  {
    path: '/ai-chat',
    name: 'AiChat',
    component: () => import('@/views/front/AiChat.vue'),
    meta: { title: 'AI智能助手', noAuth: true }
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('@/views/front/Knowledge.vue'),
    meta: { title: 'RAG 知识库', noAuth: true }
  },
  // Admin routes
  {
    path: '/admin',
    redirect: '/admin/dashboard'
  },
  {
    path: '/admin/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/admin/Dashboard.vue'),
    meta: { title: '控制台', requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/users',
    name: 'UserManage',
    component: () => import('@/views/admin/UserManage.vue'),
    meta: { title: '用户管理', requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/products',
    name: 'ProductManage',
    component: () => import('@/views/admin/ProductManage.vue'),
    meta: { title: '商品管理', requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/categories',
    name: 'CategoryManage',
    component: () => import('@/views/admin/CategoryManage.vue'),
    meta: { title: '分类管理', requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/orders',
    name: 'OrderManage',
    component: () => import('@/views/admin/OrderManage.vue'),
    meta: { title: '订单管理', requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/refunds',
    name: 'RefundManage',
    component: () => import('@/views/admin/RefundManage.vue'),
    meta: { title: '退款管理', requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/coupons',
    name: 'CouponManage',
    component: () => import('@/views/admin/CouponManage.vue'),
    meta: { title: '优惠券管理', requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/config',
    name: 'SystemConfig',
    component: () => import('@/views/admin/SystemConfig.vue'),
    meta: { title: '系统配置', requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/knowledge',
    name: 'KnowledgeManage',
    component: () => import('@/views/admin/KnowledgeManage.vue'),
    meta: { title: '知识库管理', requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/customer-service',
    name: 'AdminCustomerService',
    component: () => import('@/views/admin/CustomerService.vue'),
    meta: { title: '客服工作台', requiresAuth: true, requiresAdmin: true }
  },
  // Customer service
  {
    path: '/customer-service',
    name: 'CustomerService',
    component: () => import('@/views/chat/CustomerService.vue'),
    meta: { title: '在线客服', noAuth: true }
  },
  // 404
  {
    path: '/:pathMatch(.*)*',
    redirect: '/home'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Navigation guard
router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? to.meta.title + ' - AI智能购物商城' : 'AI智能购物商城'

  const token = getToken()

  if (to.meta.requiresAuth && !token) {
    ElMessage.warning('请先登录')
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  if (to.meta.requiresAdmin) {
    const userStr = localStorage.getItem('ai-mall-user')
    if (userStr) {
      try {
        const user = JSON.parse(userStr)
        if (user.role !== 'ADMIN') {
          ElMessage.error('无权限访问，需要管理员权限')
          next('/home')
          return
        }
      } catch {
        next('/login')
        return
      }
    } else {
      next('/login')
      return
    }
  }

  next()
})

export default router