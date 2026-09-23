<template>
  <el-footer class="footer">
    <div class="footer-inner">
      <!-- 顶部品牌 -->
      <div class="footer-top">
        <div class="footer-brand">
          <div class="logo">
            <div class="logo-icon">🛒</div>
            <span class="logo-text">AI智能购物</span>
          </div>
          <p>基于 AI 算法的智能购物商城，为你精选每一件好物</p>
        </div>
        <div class="footer-socials">
          <a href="javascript:;" title="微信">💬</a>
          <a href="javascript:;" title="微博">📱</a>
          <a href="javascript:;" title="QQ">💻</a>
          <a href="javascript:;" title="邮箱">✉️</a>
        </div>
      </div>

      <div class="footer-links">
        <div class="link-column">
          <h4>购物指南</h4>
          <a href="javascript:;">购物流程</a>
          <a href="javascript:;">会员介绍</a>
          <a href="javascript:;">常见问题</a>
        </div>
        <div class="link-column">
          <h4>配送方式</h4>
          <a href="javascript:;">上门自提</a>
          <a href="javascript:;">快递运输</a>
          <a href="javascript:;">物流配送</a>
        </div>
        <div class="link-column">
          <h4>支付方式</h4>
          <a href="javascript:;">货到付款</a>
          <a href="javascript:;">在线支付</a>
          <a href="javascript:;">分期付款</a>
        </div>
        <div class="link-column">
          <h4>售后服务</h4>
          <a href="javascript:;">退换货政策</a>
          <a href="javascript:;">退换货流程</a>
          <a href="javascript:;">联系客服</a>
        </div>
        <div class="link-column contact">
          <h4>联系我们</h4>
          <p><el-icon><Phone /></el-icon> 400-888-8888</p>
          <p><el-icon><Message /></el-icon> service@ai-mall.com</p>
          <p><el-icon><Clock /></el-icon> 工作日 9:00-18:00</p>
        </div>
      </div>
      <div class="footer-bottom">
        <div class="footer-bottom-links">
          <a href="javascript:;">关于我们</a>
          <span class="divider">|</span>
          <a href="javascript:;">联系客服</a>
          <span class="divider">|</span>
          <a href="javascript:;">隐私政策</a>
          <span class="divider">|</span>
          <a href="javascript:;">用户协议</a>
        </div>
        <p class="copyright">&copy; 2026 AI智能购物商城 - 版权所有 | 技术支持：AI技术团队</p>
      </div>
    </div>
  </el-footer>
</template>

<script setup>
</script>

<style scoped>
.footer {
  height: auto !important;
  padding: 0;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
  color: #94a3b8;
  position: relative;
  overflow: hidden;
}
.footer::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, #6366f1, transparent);
}
.footer::after {
  content: '';
  position: absolute;
  top: -100px; right: -100px;
  width: 400px; height: 400px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.15), transparent 70%);
  border-radius: 50%;
  filter: blur(40px);
  pointer-events: none;
}
.footer-inner {
  max-width: 1240px;
  margin: 0 auto;
  padding: 50px 20px 20px;
  position: relative;
  z-index: 1;
}
.footer-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 30px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
  margin-bottom: 30px;
}
.footer-brand .logo {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.footer-brand .logo-icon {
  width: 36px; height: 36px;
  border-radius: 10px;
  background: var(--gradient-primary);
  display: flex; align-items: center; justify-content: center;
  color: #fff;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}
.footer-brand .logo-text {
  font-size: 20px;
  font-weight: 800;
  color: #fff;
}
.footer-brand p {
  margin: 0;
  font-size: 14px;
  color: #64748b;
  max-width: 400px;
  line-height: 1.6;
}
.footer-socials {
  display: flex;
  gap: 12px;
}
.footer-socials a {
  width: 40px; height: 40px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.05);
  display: flex; align-items: center; justify-content: center;
  color: #94a3b8;
  font-size: 18px;
  transition: all 0.3s;
}
.footer-socials a:hover {
  background: var(--gradient-primary);
  color: #fff;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(99, 102, 241, 0.4);
}
.footer-links {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 30px;
  padding-bottom: 30px;
}
.link-column {
  min-width: 140px;
}
.link-column h4 {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 16px;
  position: relative;
  padding-bottom: 8px;
}
.link-column h4::after {
  content: '';
  position: absolute;
  bottom: 0; left: 0;
  width: 24px; height: 2px;
  background: var(--gradient-primary);
  border-radius: 2px;
}
.link-column a {
  display: block;
  color: #94a3b8;
  text-decoration: none;
  font-size: 14px;
  line-height: 2.2;
  transition: all 0.2s;
}
.link-column a:hover {
  color: #fff;
  transform: translateX(4px);
}
.link-column p {
  margin: 6px 0;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #94a3b8;
}
.contact .el-icon {
  color: #6366f1;
}
.footer-bottom {
  text-align: center;
  padding: 20px 0 0;
  border-top: 1px solid rgba(255,255,255,0.06);
}
.footer-bottom-links {
  margin-bottom: 10px;
}
.footer-bottom-links a {
  color: #94a3b8;
  text-decoration: none;
  font-size: 13px;
  margin: 0 8px;
  transition: color 0.2s;
}
.footer-bottom-links a:hover {
  color: #818cf8;
}
.divider {
  color: #475569;
}
.copyright {
  font-size: 12px;
  color: #64748b;
  margin: 0;
}
</style>