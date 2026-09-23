# ==========================================
# AI 商城一键启动脚本（双击即可）
# 启动顺序: Redis -> MinIO -> 后端 -> 前端
# ==========================================
$ErrorActionPreference = 'SilentlyContinue'

# 切到脚本所在目录（双击运行也保证路径正确）
$root = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location $root

$tools = Join-Path $root "tools"
$redisExe = Join-Path $tools "Redis-x64-5.0.14.1\redis-server.exe"
$minioExe = Join-Path $tools "minio.exe"

# 检查端口占用，提示用户
function Test-Port($port) {
    return (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) -ne $null
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  AI 商城一键启动（含 Redis + MinIO）" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 0. 启动 Redis
if (Test-Path $redisExe) {
    if (Test-Port 6379) {
        Write-Host "[Redis] 6379 已运行" -ForegroundColor Yellow
    } else {
        Write-Host "[Redis] 正在启动..." -ForegroundColor Green
        Start-Process -FilePath $redisExe -ArgumentList "--port 6379 --dir `"$tools`"" -WindowStyle Hidden
        Start-Sleep -Seconds 2
    }
} else {
    Write-Host "[Redis] 跳过（tools\Redis-x64-5.0.14.1 不存在）" -ForegroundColor DarkYellow
}

# 0.5 启动 MinIO
if (Test-Path $minioExe) {
    if (Test-Port 9000) {
        Write-Host "[MinIO] 9000 已运行" -ForegroundColor Yellow
    } else {
        Write-Host "[MinIO] 正在启动..." -ForegroundColor Green
        $env:MINIO_ROOT_USER     = "minioadmin"
        $env:MINIO_ROOT_PASSWORD = "minioadmin"
        $dataDir = Join-Path $tools "minio-data"
        New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
        Start-Process -FilePath $minioExe -ArgumentList "server `"$dataDir`" --address :9000 --console-address :9001" -WindowStyle Hidden
        Start-Sleep -Seconds 3
        Write-Host "        控制台 http://localhost:9001  (minioadmin/minioadmin)" -ForegroundColor DarkGray
    }
} else {
    Write-Host "[MinIO] 跳过（tools\minio.exe 不存在）" -ForegroundColor DarkYellow
}

# 1. 启动后端
if (Test-Port 8081) {
    Write-Host "[后端] 8081 端口已占用，跳过启动" -ForegroundColor Yellow
} else {
    Write-Host "[后端] 正在启动 Spring Boot..." -ForegroundColor Green
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-1.8"
    $env:QWEN_API_KEY = "sk-ws-H.RPPRRHR.8ZS2.MEUCIE_XT4-d-Yf83gqyuWtLxnEqYT20p7EvXIfP0icp94ODAiEAmO9NCWT-Q73kmNzYpY29SieWfC5bUMbFWjoskN_QfNE"
    Start-Process powershell -ArgumentList @(
        "-NoExit", "-Command",
        "cd '$root\backend'; `$env:JAVA_HOME='C:\Program Files\Java\jdk-1.8'; `$env:QWEN_API_KEY='$env:QWEN_API_KEY'; java -jar target\ai-shopping-mall-1.0.0.jar"
    ) -WindowStyle Normal
}

# 2. 启动前端
if (Test-Port 3000) {
    Write-Host "[前端] 3000 端口已占用，跳过启动" -ForegroundColor Yellow
} else {
    Write-Host "[前端] 正在启动 Vite..." -ForegroundColor Green
    Start-Process powershell -ArgumentList @(
        "-NoExit", "-Command",
        "cd '$root\frontend'; npm run dev"
    ) -WindowStyle Normal
}

# 3. 等待服务就绪
Write-Host "`n等待服务启动..." -ForegroundColor Cyan
$maxWait = 60
$elapsed = 0
while ($elapsed -lt $maxWait) {
    Start-Sleep -Seconds 2
    $elapsed += 2
    if ((Test-Port 8081) -and (Test-Port 3000)) {
        Write-Host "`n✓ 后端 8081 已就绪" -ForegroundColor Green
        Write-Host "✓ 前端 3000 已就绪" -ForegroundColor Green
        break
    }
    Write-Host "." -NoNewline
}

# 4. 打开浏览器
if ((Test-Port 8081) -and (Test-Port 3000)) {
    Write-Host "`n正在打开浏览器..." -ForegroundColor Cyan
    Start-Process "http://localhost:3000/home"
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "  启动完成！" -ForegroundColor Green
    Write-Host "  前端   : http://localhost:3000/home" -ForegroundColor White
    Write-Host "  后端   : http://localhost:8081" -ForegroundColor White
    Write-Host "  Swagger: http://localhost:8081/swagger-ui/" -ForegroundColor White
    Write-Host "  MinIO  : http://localhost:9001 (minioadmin/minioadmin)" -ForegroundColor White
    Write-Host "  Redis  : redis-cli -p 6379" -ForegroundColor White
    Write-Host "========================================" -ForegroundColor Cyan
} else {
    Write-Host "`n⚠ 服务未在 $maxWait 秒内就绪，请检查弹出的 PowerShell 窗口" -ForegroundColor Red
}

Write-Host "`n按任意键关闭本窗口..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
