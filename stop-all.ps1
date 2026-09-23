# ==========================================
# AI 商城一键停止脚本
# ==========================================
$ErrorActionPreference = 'SilentlyContinue'

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  AI 商城一键停止" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 停后端（8081）
$backend = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue
if ($backend) {
    $pid8081 = ($backend | Select-Object -First 1).OwningProcess
    Write-Host "[后端] 停止 PID=$pid8081" -ForegroundColor Yellow
    Stop-Process -Id $pid8081 -Force
} else {
    Write-Host "[后端] 未运行" -ForegroundColor Gray
}

# 停前端（3000）
$frontend = Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction SilentlyContinue
if ($frontend) {
    $pid3000 = ($frontend | Select-Object -First 1).OwningProcess
    Write-Host "[前端] 停止 PID=$pid3000" -ForegroundColor Yellow
    Stop-Process -Id $pid3000 -Force
} else {
    Write-Host "[前端] 未运行" -ForegroundColor Gray
}

# 顺手清理残留 npm/java 进程（可选）
$procs = Get-Process -Name "node","java" -ErrorAction SilentlyContinue | Where-Object {
    $_.MainWindowTitle -like "*ai-shopping-mall*" -or $_.Path -like "*ai-shopping-mall*"
}
foreach ($p in $procs) {
    Write-Host "[清理] 残留进程 PID=$($p.Id) $($p.ProcessName)" -ForegroundColor DarkYellow
    Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
}

Write-Host "`n✓ 停止完成" -ForegroundColor Green
Write-Host "按任意键关闭窗口..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
