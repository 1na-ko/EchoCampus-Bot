@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo =========================================
echo   EchoCampus-Bot 部署脚本 (Windows)
echo =========================================

REM 检查Docker是否安装
echo [检查] Docker是否安装...
docker --version >nul 2>&1
if errorlevel 1 (
    echo [错误] Docker未安装，请先安装Docker Desktop
    pause
    exit /b 1
)
echo [成功] Docker已安装

REM 检查Docker Compose是否安装
echo [检查] Docker Compose是否安装...
docker-compose --version >nul 2>&1
if errorlevel 1 (
    echo [错误] Docker Compose未安装，请先安装Docker Compose
    pause
    exit /b 1
)
echo [成功] Docker Compose已安装

REM 检查.env文件
echo [检查] .env文件...
if not exist .env (
    echo [错误] .env文件不存在，请复制.env.example并配置
    pause
    exit /b 1
)
echo [成功] .env文件存在

REM 询问是否停止旧容器
set /p stop_old="是否停止并删除旧容器？(y/n): "
if /i "!stop_old!"=="y" (
    echo [停止] 停止旧容器...
    docker-compose down
    echo [成功] 旧容器已停止
)

REM 构建镜像
echo [构建] 构建Docker镜像...
docker-compose build --no-cache
if errorlevel 1 (
    echo [错误] 镜像构建失败
    pause
    exit /b 1
)
echo [成功] 镜像构建完成

REM 启动服务
echo [启动] 启动服务...
docker-compose up -d
if errorlevel 1 (
    echo [错误] 服务启动失败
    pause
    exit /b 1
)
echo [成功] 服务已启动

REM 等待服务启动
echo [等待] 等待服务启动...
timeout /t 10 /nobreak >nul

REM 显示服务状态
echo [状态] 检查服务状态...
docker-compose ps

REM 显示服务信息
echo.
echo =========================================
echo   部署完成！
echo =========================================
echo.
echo 服务访问地址：
echo   - 后端API: http://localhost:8083/api
echo   - API文档: http://localhost:8083/api/doc.html
echo   - Milvus管理: http://localhost:8000
echo   - MinIO控制台: http://localhost:9001
echo.
echo 查看日志：
echo   docker-compose logs -f echocampus-bot
echo.
echo 停止服务：
echo   docker-compose down
echo.
echo 重启服务：
echo   docker-compose restart
echo.

pause
