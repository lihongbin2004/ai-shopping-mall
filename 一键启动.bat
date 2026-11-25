@echo off
chcp 65001 >nul
title AI 智能购物商城 - 一键启动
echo ============================================
echo    AI 智能购物商城 一键启动
echo    MySQL + Redis + MinIO + 后端 + 前端
echo ============================================
echo.

set "PROJECT=%~dp0"
set "TOOLS=%PROJECT%tools"
set "REDIS_DIR=%TOOLS%\Redis-x64-5.0.14.1"
set "MINIO_DIR=%TOOLS%"
set "BACKEND=%PROJECT%backend"
set "FRONTEND=%PROJECT%frontend"
set "JAVA_HOME=C:\Program Files\Java\jdk-1.8"

rem ===== 1. MySQL (Windows 服务) =====
echo [1/5] 检查 MySQL...
sc query MySQL80 | findstr RUNNING >nul 2>&1
if errorlevel 1 (
    echo       启动 MySQL 服务...
    net start MySQL80 >nul 2>&1
) else (
    echo       MySQL 已在运行
)

rem ===== 2. Redis =====
echo [2/5] 启动 Redis (端口 6379)...
start "Redis" cmd /k ""%REDIS_DIR%\redis-server.exe" --port 6379 --dir "%REDIS_DIR%""
timeout /t 3 /nobreak >nul

rem ===== 3. MinIO =====
echo [3/5] 启动 MinIO (端口 9000/9001)...
if not exist "%MINIO_DIR%\minio-data" mkdir "%MINIO_DIR%\minio-data"
set MINIO_ROOT_USER=minioadmin
set MINIO_ROOT_PASSWORD=minioadmin
start "MinIO" cmd /k ""%MINIO_DIR%\minio.exe" server "%MINIO_DIR%\minio-data" --address :9000 --console-address :9001"
timeout /t 4 /nobreak >nul

rem ===== 4. 后端 Spring Boot =====
echo [4/5] 启动后端 Spring Boot (端口 8081)...
set JAVA_HOME=C:\Program Files\Java\jdk-1.8
set PATH=%JAVA_HOME%\bin;%PATH%
start "Backend" cmd /k "cd /d "%BACKEND%" && java -jar target\ai-shopping-mall-1.0.0.jar"

rem ===== 5. 前端 Vite =====
echo [5/5] 启动前端 Vite (端口 3000)...
start "Frontend" cmd /k "cd /d "%FRONTEND%" && npm run dev"

echo.
echo ============================================
echo    所有服务已启动！访问：
echo    前端商城:   http://localhost:3000
echo    后端接口:   http://localhost:8081
echo    MinIO:      http://localhost:9001 (admin/minioadmin)
echo    Swagger:    http://localhost:8081/swagger-ui/
echo ============================================
echo.
echo 关闭此窗口不会停止服务，
echo 要停止请手动关闭各命令行窗口。
pause
