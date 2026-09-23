import { onMounted, onUnmounted, nextTick } from 'vue'

/**
 * 滚动入场动画 composable
 * 给带有 .reveal / .reveal-left / .reveal-right / .reveal-scale 的元素添加 .is-visible，
 * 配合 global.css 中的过渡定义即可在元素进入视口时优雅显示。
 */
export function useReveal(rootRef, options = {}) {
  const {
    selector = '.reveal, .reveal-left, .reveal-right, .reveal-scale',
    threshold = 0.12,
    rootMargin = '0px 0px -40px 0px',
    once = true
  } = options

  let observer = null
  let pendingTimer = null

  function scanAndObserve(root) {
    if (!observer) return
    root.querySelectorAll(selector).forEach((el) => {
      // 已经可见的跳过
      if (el.classList.contains('is-visible')) return
      const rect = el.getBoundingClientRect()
      if (rect.top < window.innerHeight && rect.bottom > 0) {
        el.classList.add('is-visible')
      } else {
        observer.observe(el)
      }
    })
  }

  function init() {
    if (typeof window === 'undefined' || !('IntersectionObserver' in window)) {
      const root = rootRef?.value || document
      root.querySelectorAll(selector).forEach((el) => el.classList.add('is-visible'))
      return
    }

    observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible')
            if (once) observer.unobserve(entry.target)
          } else if (!once) {
            entry.target.classList.remove('is-visible')
          }
        })
      },
      { threshold, rootMargin }
    )

    const root = rootRef?.value || document
    scanAndObserve(root)
  }

  // 暴露给组件，用于在动态内容渲染后重新扫描
  function refresh() {
    if (pendingTimer) {
      clearTimeout(pendingTimer)
      pendingTimer = null
    }
    pendingTimer = setTimeout(() => {
      const root = rootRef?.value || document
      scanAndObserve(root)
    }, 30)
  }

  onMounted(() => {
    // 等一帧确保 DOM 完成
    requestAnimationFrame(init)
    // 再延迟一帧扫描一次，捕获首屏之后由异步数据渲染出的元素
    setTimeout(refresh, 120)
  })

  onUnmounted(() => {
    if (observer) observer.disconnect()
    if (pendingTimer) clearTimeout(pendingTimer)
  })

  return { refresh }
}
