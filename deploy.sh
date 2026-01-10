#!/bin/bash

set -e

echo "========================================="
echo "  EchoCampus-Bot 部署脚本"
echo "========================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 检查Docker是否安装
check_docker() {
    echo -e "${YELLOW}检查Docker是否安装...${NC}"
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}Docker未安装，请先安装Docker${NC}"
        exit 1
    fi
    echo -e "${GREEN}Docker已安装${NC}"
}

# 检查Docker Compose是否安装
check_docker_compose() {
    echo -e "${YELLOW}检查Docker Compose是否安装...${NC}"
    if ! command -v docker-compose &> /dev/null; then
        echo -e "${RED}Docker Compose未安装，请先安装Docker Compose${NC}"
        exit 1
    fi
    echo -e "${GREEN}Docker Compose已安装${NC}"
}

# 检查.env文件
check_env_file() {
    echo -e "${YELLOW}检查.env文件...${NC}"
    if [ ! -f .env ]; then
        echo -e "${RED}.env文件不存在，请复制.env.example并配置${NC}"
        exit 1
    fi
    echo -e "${GREEN}.env文件存在${NC}"
}

# 停止并删除旧容器
stop_old_containers() {
    echo -e "${YELLOW}停止旧容器...${NC}"
    docker-compose down
    echo -e "${GREEN}旧容器已停止${NC}"
}

# 构建镜像
build_images() {
    echo -e "${YELLOW}构建Docker镜像...${NC}"
    docker-compose build --no-cache
    echo -e "${GREEN}镜像构建完成${NC}"
}

# 启动服务
start_services() {
    echo -e "${YELLOW}启动服务...${NC}"
    docker-compose up -d
    echo -e "${GREEN}服务已启动${NC}"
}

# 等待服务健康
wait_for_services() {
    echo -e "${YELLOW}等待服务启动...${NC}"
    sleep 10
    
    echo -e "${YELLOW}检查服务状态...${NC}"
    docker-compose ps
    
    echo -e "${YELLOW}等待PostgreSQL就绪...${NC}"
    until docker-compose exec -T postgres pg_isready -U postgres; do
        echo "PostgreSQL还未就绪，等待中..."
        sleep 5
    done
    echo -e "${GREEN}PostgreSQL已就绪${NC}"
    
    echo -e "${YELLOW}等待Milvus就绪...${NC}"
    until docker-compose exec -T milvus-standalone curl -f http://localhost:9091/healthz &> /dev/null; do
        echo "Milvus还未就绪，等待中..."
        sleep 5
    done
    echo -e "${GREEN}Milvus已就绪${NC}"
    
    echo -e "${YELLOW}等待后端服务就绪...${NC}"
    until docker-compose exec -T echocampus-bot curl -f http://localhost:8080/api/health &> /dev/null; do
        echo "后端服务还未就绪，等待中..."
        sleep 5
    done
    echo -e "${GREEN}后端服务已就绪${NC}"
}

# 显示服务信息
show_service_info() {
    echo ""
    echo "========================================="
    echo "  部署完成！"
    echo "========================================="
    echo ""
    echo "服务访问地址："
    echo "  - 后端API: http://localhost:8083/api"
    echo "  - API文档: http://localhost:8083/api/doc.html"
    echo "  - Milvus管理: http://localhost:8000"
    echo "  - MinIO控制台: http://localhost:9001"
    echo ""
    echo "查看日志："
    echo "  docker-compose logs -f echocampus-bot"
    echo ""
    echo "停止服务："
    echo "  docker-compose down"
    echo ""
    echo "重启服务："
    echo "  docker-compose restart"
    echo ""
}

# 主流程
main() {
    check_docker
    check_docker_compose
    check_env_file
    
    read -p "是否停止并删除旧容器？(y/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        stop_old_containers
    fi
    
    build_images
    start_services
    wait_for_services
    show_service_info
}

# 执行主流程
main
