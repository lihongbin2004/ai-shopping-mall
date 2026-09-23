<template>
  <div class="logistics-map-section">
    <div class="logistics-header">
      <h4>🚚 物流追踪</h4>
      <div class="logistics-meta" v-if="tracks.length">
        <span class="company">{{ meta.company || '模拟快递' }}</span>
        <span class="tracking-no">运单号 {{ meta.trackingNo }}</span>
      </div>
    </div>

    <div class="map-and-timeline">
      <!-- 地图区域 -->
      <div class="map-wrap">
        <canvas ref="mapCanvas" :width="canvasW" :height="canvasH"></canvas>
        <div v-if="tracks.length >= 2" class="map-legend">
          <span><i class="dot start"></i>起点</span>
          <span><i class="dot current"></i>当前位置</span>
          <span><i class="dot end"></i>终点</span>
        </div>
      </div>

      <!-- 时间轴区域 -->
      <div class="timeline-wrap" v-if="tracks.length">
        <div class="timeline">
          <div
            v-for="(t, idx) in tracks"
            :key="idx"
            class="timeline-item"
            :class="'tl-' + (t.type || 'INFO').toLowerCase()"
          >
            <div class="tl-dot" :class="{ active: idx === tracks.length - 1 }"></div>
            <div class="tl-content">
              <div class="tl-city">{{ t.city }}</div>
              <div class="tl-desc">{{ t.description }}</div>
              <div class="tl-time">{{ formatTime(t.time) }}</div>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无物流信息" :image-size="60" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { getLogisticsTrack } from '@/api/order'

const props = defineProps({ orderId: { type: [Number, String], required: true } })

const canvasW = 560
const canvasH = 380
const mapCanvas = ref(null)
const tracks = ref([])
const meta = ref({})

let animTimer = null
let truckProgress = 0   // 0~1 在路径上的进度
let pathPoints = []     // 计算后的画布坐标点

// 中国地图经纬度范围
const LNG_MIN = 73, LNG_MAX = 135   // 经度
const LAT_MIN = 18, LAT_MAX = 54    // 纬度

// 简化版中国轮廓点（用于背景绘制，不是精确地图）
const CHINA_OUTLINE = [
  [80, 44], [85, 45], [92, 48], [100, 50], [110, 53], [120, 53], [128, 50], [135, 48],
  [134, 45], [130, 42], [128, 39], [125, 37], [122, 35], [121, 31], [122, 28], [120, 25],
  [118, 23], [115, 22], [110, 21], [108, 20], [105, 22], [102, 23], [98, 24], [95, 25],
  [89, 28], [82, 30], [78, 32], [75, 35], [73, 40], [76, 43], [80, 44]
]

function lngLatToXY(lng, lat) {
  const x = ((lng - LNG_MIN) / (LNG_MAX - LNG_MIN)) * canvasW
  const y = canvasH - ((lat - LAT_MIN) / (LAT_MAX - LAT_MIN)) * canvasH
  return { x, y }
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  if (isNaN(d.getTime())) return t
  const pad = n => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadTracks() {
  try {
    const res = await getLogisticsTrack(props.orderId)
    const data = res.data || {}
    tracks.value = data.tracks || []
    meta.value = { company: data.company, trackingNo: data.trackingNo, orderNo: data.orderNo }
    await nextTick()
    drawMap()
    if (tracks.value.length >= 2) {
      startAnimation()
    } else {
      stopAnimation()
    }
  } catch (e) {
    console.warn('物流轨迹加载失败', e)
  }
}

function drawMap() {
  const canvas = mapCanvas.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, canvasW, canvasH)

  // 背景渐变
  const bgGrad = ctx.createLinearGradient(0, 0, 0, canvasH)
  bgGrad.addColorStop(0, '#e8f0fe')
  bgGrad.addColorStop(1, '#f5f9ff')
  ctx.fillStyle = bgGrad
  ctx.fillRect(0, 0, canvasW, canvasH)

  // 网格线
  ctx.strokeStyle = 'rgba(99,102,241,0.08)'
  ctx.lineWidth = 1
  for (let i = 0; i <= 8; i++) {
    ctx.beginPath()
    ctx.moveTo((canvasW / 8) * i, 0)
    ctx.lineTo((canvasW / 8) * i, canvasH)
    ctx.stroke()
  }
  for (let i = 0; i <= 6; i++) {
    ctx.beginPath()
    ctx.moveTo(0, (canvasH / 6) * i)
    ctx.lineTo(canvasW, (canvasH / 6) * i)
    ctx.stroke()
  }

  // 中国轮廓
  ctx.beginPath()
  CHINA_OUTLINE.forEach((pt, i) => {
    const { x, y } = lngLatToXY(pt[0], pt[1])
    if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y)
  })
  ctx.closePath()
  ctx.fillStyle = 'rgba(99,102,241,0.12)'
  ctx.fill()
  ctx.strokeStyle = 'rgba(99,102,241,0.45)'
  ctx.lineWidth = 2
  ctx.stroke()

  if (tracks.value.length < 2) {
    drawSinglePoint()
    return
  }

  // 计算所有轨迹点的画布坐标
  pathPoints = tracks.value.map(t => lngLatToXY(t.lng, t.lat))

  // 绘制线路（带虚线效果，逐段高亮）
  for (let i = 0; i < pathPoints.length - 1; i++) {
    const p1 = pathPoints[i], p2 = pathPoints[i + 1]
    // 当前段已经过（实线）vs 未经过（虚线）
    const segStart = i / (pathPoints.length - 1)
    const segEnd = (i + 1) / (pathPoints.length - 1)
    const isPassed = truckProgress.value >= segEnd

    ctx.beginPath()
    ctx.moveTo(p1.x, p1.y)
    ctx.lineTo(p2.x, p2.y)
    ctx.strokeStyle = isPassed ? '#6366f1' : 'rgba(99,102,241,0.3)'
    ctx.lineWidth = isPassed ? 3 : 2
    if (!isPassed) ctx.setLineDash([6, 4]); else ctx.setLineDash([])
    ctx.stroke()
    ctx.setLineDash([])
  }

  // 绘制城市点
  pathPoints.forEach((p, i) => {
    const isStart = i === 0
    const isEnd = i === pathPoints.length - 1
    const isPassed = truckProgress.value >= i / (pathPoints.length - 1)
    const color = isEnd ? '#ef4444' : (isStart ? '#22c55e' : (isPassed ? '#6366f1' : '#94a3b8'))
    const r = isStart || isEnd ? 8 : 6

    // 脉冲光晕
    if (isPassed) {
      ctx.beginPath()
      ctx.arc(p.x, p.y, r + 8, 0, Math.PI * 2)
      const glow = ctx.createRadialGradient(p.x, p.y, r, p.x, p.y, r + 8)
      glow.addColorStop(0, color + '66')
      glow.addColorStop(1, color + '00')
      ctx.fillStyle = glow
      ctx.fill()
    }

    // 实心点
    ctx.beginPath()
    ctx.arc(p.x, p.y, r, 0, Math.PI * 2)
    ctx.fillStyle = color
    ctx.fill()
    ctx.strokeStyle = '#fff'
    ctx.lineWidth = 2
    ctx.stroke()

    // 城市名
    ctx.fillStyle = '#1e293b'
    ctx.font = 'bold 12px -apple-system, "PingFang SC", sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText(tracks.value[i].city, p.x, p.y - r - 6)

    // 起点/终点标签
    if (isStart) {
      ctx.fillStyle = '#22c55e'
      ctx.font = 'bold 10px sans-serif'
      ctx.fillText('起', p.x, p.y + r + 14)
    }
    if (isEnd) {
      ctx.fillStyle = '#ef4444'
      ctx.font = 'bold 10px sans-serif'
      ctx.fillText('终', p.x, p.y + r + 14)
    }
  })

  // 绘制卡车（在当前进度位置）
  drawTruck(getTruckPosition())
}

function drawSinglePoint() {
  const t = tracks.value[0]
  if (!t) return
  const { x, y } = lngLatToXY(t.lng, t.lat)
  ctx.beginPath()
  ctx.arc(x, y, 10, 0, Math.PI * 2)
  ctx.fillStyle = '#6366f1'
  ctx.fill()
  ctx.strokeStyle = '#fff'
  ctx.lineWidth = 2
  ctx.stroke()
  ctx.fillStyle = '#1e293b'
  ctx.font = 'bold 13px sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText(t.city, x, y - 18)
}

function getTruckPosition() {
  if (pathPoints.length < 2) return pathPoints[0] || { x: canvasW / 2, y: canvasH / 2 }
  const totalSegs = pathPoints.length - 1
  const rawSeg = truckProgress.value * totalSegs
  const segIdx = Math.min(Math.floor(rawSeg), totalSegs - 1)
  const segT = rawSeg - segIdx
  const p1 = pathPoints[segIdx]
  const p2 = pathPoints[segIdx + 1]
  return {
    x: p1.x + (p2.x - p1.x) * segT,
    y: p1.y + (p2.y - p1.y) * segT
  }
}

function drawTruck(pos) {
  const ctx = mapCanvas.value.getContext('2d')
  // 光晕
  ctx.beginPath()
  ctx.arc(pos.x, pos.y, 14, 0, Math.PI * 2)
  const glow = ctx.createRadialGradient(pos.x, pos.y, 4, pos.x, pos.y, 14)
  glow.addColorStop(0, 'rgba(251,146,60,0.5)')
  glow.addColorStop(1, 'rgba(251,146,60,0)')
  ctx.fillStyle = glow
  ctx.fill()

  // 卡车图标（简化车身+车头+轮子，手绘兼容所有浏览器）
  ctx.fillStyle = '#f97316'
  ctx.strokeStyle = '#fff'
  ctx.lineWidth = 2
  const tx = pos.x, ty = pos.y
  // 车身（矩形）
  ctx.beginPath()
  ctx.rect(tx - 12, ty - 7, 20, 12)
  ctx.fill(); ctx.stroke()
  // 车头（小矩形）
  ctx.beginPath()
  ctx.rect(tx + 6, ty - 4, 8, 9)
  ctx.fill(); ctx.stroke()
  // 轮子
  ctx.fillStyle = '#1e293b'
  ctx.beginPath(); ctx.arc(tx - 7, ty + 6, 2.5, 0, Math.PI * 2); ctx.fill()
  ctx.beginPath(); ctx.arc(tx + 5, ty + 6, 2.5, 0, Math.PI * 2); ctx.fill()
}

function startAnimation() {
  stopAnimation()
  truckProgress.value = 0
  const totalSegs = pathPoints.length - 1
  const animDuration = 6000 + totalSegs * 2000  // 段越多越慢
  const startTime = Date.now()

  function tick() {
    const elapsed = Date.now() - startTime
    // 循环播放：到终点后停留 1.5s 再重头
    const cycleTime = animDuration + 1500
    const modElapsed = elapsed % cycleTime
    if (modElapsed < animDuration) {
      // 缓动
      const raw = modElapsed / animDuration
      truckProgress.value = easeInOutCubic(raw)
    } else {
      truckProgress.value = 1
    }
    drawMap()
    animTimer = requestAnimationFrame(tick)
  }
  animTimer = requestAnimationFrame(tick)
}

function stopAnimation() {
  if (animTimer) {
    cancelAnimationFrame(animTimer)
    animTimer = null
  }
}

function easeInOutCubic(t) {
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2
}

onMounted(loadTracks)
watch(() => props.orderId, loadTracks)
onBeforeUnmount(stopAnimation)
</script>

<style scoped>
.logistics-map-section {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  margin-top: 16px;
  border: 1px solid rgba(99, 102, 241, 0.1);
}
.logistics-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.logistics-header h4 {
  margin: 0;
  font-size: 16px;
  color: #1e293b;
}
.logistics-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #64748b;
}
.logistics-meta .company {
  background: #6366f1;
  color: #fff;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 11px;
}
.logistics-meta .tracking-no {
  font-family: 'Courier New', monospace;
}

.map-and-timeline {
  display: flex;
  gap: 20px;
}
@media (max-width: 900px) {
  .map-and-timeline { flex-direction: column; }
}

.map-wrap {
  position: relative;
  flex-shrink: 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(99, 102, 241, 0.15);
}
.map-wrap canvas { display: block; }
.map-legend {
  position: absolute;
  bottom: 8px;
  left: 8px;
  display: flex;
  gap: 14px;
  background: rgba(255,255,255,0.9);
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 11px;
  color: #64748b;
  backdrop-filter: blur(4px);
}
.map-legend .dot {
  display: inline-block;
  width: 8px; height: 8px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: middle;
}
.map-legend .dot.start   { background: #22c55e; }
.map-legend .dot.current { background: #f97316; box-shadow: 0 0 6px rgba(249,115,22,0.6); }
.map-legend .dot.end     { background: #ef4444; }

.timeline-wrap {
  flex: 1;
  max-height: 380px;
  overflow-y: auto;
}
.timeline {
  position: relative;
  padding-left: 18px;
}
.timeline::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 0; bottom: 0;
  width: 2px;
  background: linear-gradient(to bottom, #6366f1, rgba(99,102,241,0.15));
}
.timeline-item {
  position: relative;
  padding: 8px 0 14px 8px;
}
.tl-dot {
  position: absolute;
  left: -17px;
  top: 14px;
  width: 12px; height: 12px;
  border-radius: 50%;
  background: #94a3b8;
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px #94a3b8;
}
.timeline-item.tl-success .tl-dot { background: #22c55e; box-shadow: 0 0 0 1px #22c55e; }
.timeline-item.tl-info    .tl-dot { background: #6366f1; box-shadow: 0 0 0 1px #6366f1; }
.timeline-item.tl-warning .tl-dot { background: #f97316; box-shadow: 0 0 0 1px #f97316; }
.timeline-item.tl-error   .tl-dot { background: #ef4444; box-shadow: 0 0 0 1px #ef4444; }
.timeline-item .tl-dot.active {
  animation: pulse 1.6s infinite;
}
@keyframes pulse {
  0%   { box-shadow: 0 0 0 1px currentColor; }
  50%  { box-shadow: 0 0 0 6px rgba(99,102,241,0.2); }
  100% { box-shadow: 0 0 0 1px currentColor; }
}

.tl-content .tl-city {
  font-weight: 600;
  font-size: 13px;
  color: #1e293b;
}
.tl-content .tl-desc {
  font-size: 12px;
  color: #475569;
  margin-top: 2px;
}
.tl-content .tl-time {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
}
</style>
