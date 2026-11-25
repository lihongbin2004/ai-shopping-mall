# AI 商城启动脚本 - 请不要关闭此窗口或子窗口
# =============================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  AI 智能购物商城 - 完整启动" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$root = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location $root

# --- 1. 启 Redis ---
Write-Host "[1/4] 启动 Redis (6379)..." -ForegroundColor Yellow
$redisExe = Join-Path $root "tools\Redis-x64-5.0.14.1\redis-server.exe"
if ((Test-Path $redisExe) -and -not (Get-NetTCPConnection -LocalPort 6379 -ErrorAction SilentlyContinue)) {
    Start-Process -FilePath $redisExe -ArgumentList "--port","6379","--dir","`"$root\tools`"" -WorkingDirectory (Join-Path $root "tools")
    Write-Host "      ✓ Redis 已启动" -ForegroundColor Green
} elseif (Get-NetTCPConnection -LocalPort 6379 -ErrorAction SilentlyContinue) {
    Write-Host "      ✓ Redis 已在运行" -ForegroundColor Green
} else {
    Write-Host "      ✗ Redis exe 未找到: $redisExe" -ForegroundColor Red
}

# --- 2. 启 MinIO ---
Write-Host "[2/4] 启动 MinIO (9000/9001)..." -ForegroundColor Yellow
$minioExe = Join-Path $root "tools\minio.exe"
$env:MINIO_ROOT_USER = "minioadmin"
$env:MINIO_ROOT_PASSWORD = "minioadmin"
$dataDir = Join-Path $root "tools\minio-data"
New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
if ((Test-Path $minioExe) -and -not (Get-NetTCPConnection -LocalPort 9000 -ErrorAction SilentlyContinue)) {
    Start-Process -FilePath $minioExe -ArgumentList "server","`"$dataDir`"","--address",":9000","--console-address",":9001"
    Write-Host "      ✓ MinIO 已启动  控制台: http://localhost:9001" -ForegroundColor Green
} elseif (Get-NetTCPConnection -LocalPort 9000 -ErrorAction SilentlyContinue) {
    Write-Host "      ✓ MinIO 已在运行" -ForegroundColor Green
} else {
    Write-Host "      ✗ MinIO exe 未找到: $minioExe" -ForegroundColor Red
}

# --- 等中间件就绪 ---
Write-Host "      等待中间件就绪..." -ForegroundColor Cyan
for ($i=0; $i -lt 15; $i++) {
    Start-Sleep -Milliseconds 500
    $r = Test-NetConnection -ComputerName localhost -Port 6379 -WarningAction SilentlyContinue -InformationAction SilentlyContinue | Select-Object -ExpandProperty TcpTestSucceeded
    $m = Test-NetConnection -ComputerName localhost -Port 9000 -WarningAction SilentlyContinue -InformationAction SilentlyContinue | Select-Object -ExpandProperty TcpTestSucceeded
    if ($r -and $m) { break }
}

# --- 3. 启后端 ---
Write-Host "[3/4] 启动 Spring Boot 后端 (8081)..." -ForegroundColor Yellow
$backend = Join-Path $root "backend"
$jar = Join-Path $backend "target\ai-shopping-mall-1.0.0.jar"
if ((Test-Path $jar) -and -not (Get-NetTCPConnection -LocalPort 8081 -ErrorAction SilentlyContinue)) {
    Start-Process -FilePath "java" -ArgumentList "-jar","$jar" -WorkingDirectory $backend -WindowStyle Minimized
    Write-Host "      ✓ 后端启动中..." -ForegroundColor Green
} elseif (Get-NetTCPConnection -LocalPort 8081 -ErrorAction SilentlyContinue) {
    Write-Host "      ✓ 后端已在运行" -ForegroundColor Green
} else {
    Write-Host "      ✗ jar 未找到: $jar" -ForegroundColor Red
}

# --- 4. 启前端 ---
Write-Host "[4/4] 启动 Vite 前端 (3000)..." -ForegroundColor Yellow
$frontend = Join-Path $root "frontend"
if ((Test-Path (Join-Path $frontend "package.json")) -and -not (Get-NetTCPConnection -LocalPort 3000 -ErrorAction SilentlyContinue)) {
    Start-Process -FilePath "powershell" -ArgumentList "-NoExit","-Command","cd '$frontend'; npm run dev"
    Write-Host "      ✓ 前端启动中..." -ForegroundColor Green
} elseif (Get-NetTCPConnection -LocalPort 3000 -ErrorAction SilentlyContinue) {
    Write-Host "      ✓ 前端已在运行" -ForegroundColor Green
} else {
    Write-Host "      ✗ 前端目录未找到" -ForegroundColor Red
}

# --- 等待后端就绪 ---
Write-Host ""
Write-Host "等待后端就绪 (最多 60s)..." -ForegroundColor Cyan
$maxWait = 60
$elapsed = 0
while ($elapsed -lt $maxWait) {
    Start-Sleep -Seconds 2
    $elapsed += 2
    $b = Test-NetConnection -ComputerName localhost -Port 8081 -WarningAction SilentlyContinue -InformationAction SilentlyContinue | Select-Object -ExpandProperty TcpTestSucceeded
    if ($b) {
        Write-Host "      ✓ 后端已就绪 (${elapsed}s)" -ForegroundColor Green
        break
    }
    Write-Host "." -NoNewline
}

# --- 最终状态 ---
Write-Host ""
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  服务状态总览" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
foreach ($s in @(
    @{Name="MySQL"; Port=3306},
    @{Name="Redis"; Port=6379},
    @{Name="MinIO"; Port=9000},
    @{Name="后端 Spring Boot"; Port=8081},
    @{Name="前端 Vite"; Port=3000}
)) {
    $ok = Test-NetConnection -ComputerName localhost -Port $s.Port -WarningAction SilentlyContinue -InformationAction SilentlyContinue | Select-Object -ExpandProperty TcpTestSucceeded
    $mark = if ($ok) { "✓" } else { "✗" }
    $color = if ($ok) { "Green" } else { "Red" }
    Write-Host "  $mark $($s.Name) :$($s.Port)" -ForegroundColor $color
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  访问地址" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  前端首页: http://localhost:3000" -ForegroundColor White
Write-Host "  后端 API: http://localhost:8081/api/product/page?page=1&size=5" -ForegroundColor White
Write-Host "  Swagger : http://localhost:8081/swagger-ui/" -ForegroundColor White
Write-Host "  MinIO   : http://localhost:9001 (minioadmin/minioadmin)" -ForegroundColor White
Write-Host ""
Write-Host "  ⚠ 请勿关闭此窗口及弹出的 Redis/MinIO 窗口!" -ForegroundColor Yellow

# 打开浏览器
$fe = Test-NetConnection -ComputerName localhost -Port 3000 -WarningAction SilentlyContinue -InformationAction SilentlyContinue | Select-Object -ExpandProperty TcpTestSucceeded
if ($fe) { Start-Process "http://localhost:3000/home" }
