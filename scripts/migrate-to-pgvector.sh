#!/bin/bash
# ==========================================
# Milvus到pgvector数据迁移脚本
# 通过API调用后端迁移服务
# ==========================================

set -e

# 配置
API_BASE_URL="${API_BASE_URL:-http://localhost:8083/api}"
BATCH_SIZE="${BATCH_SIZE:-50}"
REINDEX="${REINDEX:-true}"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

echo_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

echo_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查API是否可用
check_api_health() {
    echo_info "检查API服务状态..."
    
    response=$(curl -s -o /dev/null -w "%{http_code}" "${API_BASE_URL}/v1/health" 2>/dev/null || echo "000")
    
    if [ "$response" = "200" ]; then
        echo_info "API服务正常"
        return 0
    else
        echo_error "API服务不可用 (HTTP: $response)"
        return 1
    fi
}

# 启动迁移
start_migration() {
    echo_info "启动迁移任务..."
    echo_info "  批量大小: $BATCH_SIZE"
    echo_info "  重建索引: $REINDEX"
    
    response=$(curl -s -X POST \
        "${API_BASE_URL}/v1/admin/migration/milvus-to-pgvector?batchSize=${BATCH_SIZE}&reindex=${REINDEX}" \
        -H "Content-Type: application/json")
    
    echo "$response"
    
    # 检查是否成功启动
    if echo "$response" | grep -q '"code":200'; then
        echo_info "迁移任务已启动"
        return 0
    else
        echo_error "迁移任务启动失败"
        return 1
    fi
}

# 查询迁移进度
check_progress() {
    response=$(curl -s "${API_BASE_URL}/v1/admin/migration/progress")
    echo "$response"
}

# 监控迁移进度
monitor_migration() {
    echo_info "监控迁移进度..."
    
    while true; do
        progress_json=$(check_progress)
        
        # 解析进度信息
        status=$(echo "$progress_json" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
        processed=$(echo "$progress_json" | grep -o '"processedRecords":[0-9]*' | cut -d':' -f2)
        total=$(echo "$progress_json" | grep -o '"totalRecords":[0-9]*' | cut -d':' -f2)
        phase=$(echo "$progress_json" | grep -o '"currentPhase":"[^"]*"' | cut -d'"' -f4)
        
        # 计算百分比
        if [ -n "$total" ] && [ "$total" -gt 0 ]; then
            percent=$((processed * 100 / total))
        else
            percent=0
        fi
        
        echo -ne "\r进度: $processed/$total ($percent%) - 阶段: $phase - 状态: $status    "
        
        # 检查是否完成
        case "$status" in
            "COMPLETED")
                echo ""
                echo_info "迁移完成!"
                return 0
                ;;
            "FAILED")
                echo ""
                echo_error "迁移失败!"
                error_msg=$(echo "$progress_json" | grep -o '"errorMessage":"[^"]*"' | cut -d'"' -f4)
                echo_error "错误信息: $error_msg"
                return 1
                ;;
            "CANCELLED")
                echo ""
                echo_warn "迁移已取消"
                return 1
                ;;
        esac
        
        sleep 2
    done
}

# 验证迁移结果
validate_migration() {
    echo_info "验证迁移结果..."
    
    response=$(curl -s -X POST "${API_BASE_URL}/v1/admin/migration/validate")
    
    if echo "$response" | grep -q '"data":true'; then
        echo_info "迁移验证通过!"
        return 0
    else
        echo_error "迁移验证失败!"
        echo "$response"
        return 1
    fi
}

# 主流程
main() {
    echo "========================================"
    echo "  Milvus -> pgvector 数据迁移工具"
    echo "========================================"
    echo ""
    
    # 1. 检查API健康状态
    if ! check_api_health; then
        echo_error "请确保后端服务已启动"
        exit 1
    fi
    
    # 2. 启动迁移
    echo ""
    if ! start_migration; then
        exit 1
    fi
    
    # 3. 监控进度
    echo ""
    if ! monitor_migration; then
        exit 1
    fi
    
    # 4. 验证结果
    echo ""
    if ! validate_migration; then
        echo_warn "建议手动检查数据完整性"
    fi
    
    echo ""
    echo_info "迁移流程完成!"
    echo ""
    echo "后续步骤建议："
    echo "  1. 手动验证向量搜索功能"
    echo "  2. 对比迁移前后的搜索结果"
    echo "  3. 确认无误后可清理Milvus数据"
}

# 执行主流程
main "$@"
