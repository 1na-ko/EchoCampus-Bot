# PowerShell版本的迁移脚本 (Windows)
# ==========================================
# Milvus到pgvector数据迁移脚本
# 通过API调用后端迁移服务
# ==========================================

param(
    [string]$ApiBaseUrl = "http://localhost:8083/api",
    [int]$BatchSize = 50,
    [bool]$Reindex = $true
)

# 颜色输出函数
function Write-Info { param($Message) Write-Host "[INFO] $Message" -ForegroundColor Green }
function Write-Warn { param($Message) Write-Host "[WARN] $Message" -ForegroundColor Yellow }
function Write-Err { param($Message) Write-Host "[ERROR] $Message" -ForegroundColor Red }

# 检查API健康状态
function Test-ApiHealth {
    Write-Info "检查API服务状态..."
    
    try {
        $response = Invoke-WebRequest -Uri "$ApiBaseUrl/v1/health" -Method Get -UseBasicParsing -TimeoutSec 10
        if ($response.StatusCode -eq 200) {
            Write-Info "API服务正常"
            return $true
        }
    }
    catch {
        Write-Err "API服务不可用: $($_.Exception.Message)"
        return $false
    }
    
    return $false
}

# 启动迁移
function Start-Migration {
    Write-Info "启动迁移任务..."
    Write-Info "  批量大小: $BatchSize"
    Write-Info "  重建索引: $Reindex"
    
    try {
        $url = "$ApiBaseUrl/v1/admin/migration/milvus-to-pgvector?batchSize=$BatchSize&reindex=$($Reindex.ToString().ToLower())"
        $response = Invoke-RestMethod -Uri $url -Method Post -ContentType "application/json"
        
        if ($response.code -eq 200) {
            Write-Info "迁移任务已启动"
            Write-Host $response.message
            return $true
        }
        else {
            Write-Err "迁移任务启动失败: $($response.message)"
            return $false
        }
    }
    catch {
        Write-Err "请求失败: $($_.Exception.Message)"
        return $false
    }
}

# 获取迁移进度
function Get-MigrationProgress {
    try {
        $response = Invoke-RestMethod -Uri "$ApiBaseUrl/v1/admin/migration/progress" -Method Get
        return $response.data
    }
    catch {
        return $null
    }
}

# 监控迁移进度
function Watch-MigrationProgress {
    Write-Info "监控迁移进度..."
    
    while ($true) {
        $progress = Get-MigrationProgress
        
        if ($null -eq $progress) {
            Write-Warn "无法获取进度信息"
            Start-Sleep -Seconds 2
            continue
        }
        
        $percent = 0
        if ($progress.totalRecords -gt 0) {
            $percent = [math]::Round(($progress.processedRecords / $progress.totalRecords) * 100)
        }
        
        $status = $progress.status
        $phase = $progress.currentPhase
        $processed = $progress.processedRecords
        $total = $progress.totalRecords
        
        Write-Host "`r进度: $processed/$total ($percent%) - 阶段: $phase - 状态: $status    " -NoNewline
        
        switch ($status) {
            "COMPLETED" {
                Write-Host ""
                Write-Info "迁移完成!"
                return $true
            }
            "FAILED" {
                Write-Host ""
                Write-Err "迁移失败!"
                Write-Err "错误信息: $($progress.errorMessage)"
                return $false
            }
            "CANCELLED" {
                Write-Host ""
                Write-Warn "迁移已取消"
                return $false
            }
        }
        
        Start-Sleep -Seconds 2
    }
}

# 验证迁移结果
function Test-MigrationResult {
    Write-Info "验证迁移结果..."
    
    try {
        $response = Invoke-RestMethod -Uri "$ApiBaseUrl/v1/admin/migration/validate" -Method Post
        
        if ($response.data -eq $true) {
            Write-Info "迁移验证通过!"
            return $true
        }
        else {
            Write-Err "迁移验证失败!"
            return $false
        }
    }
    catch {
        Write-Err "验证请求失败: $($_.Exception.Message)"
        return $false
    }
}

# 主流程
function Start-MigrationProcess {
    Write-Host "========================================"
    Write-Host "  Milvus -> pgvector 数据迁移工具"
    Write-Host "========================================"
    Write-Host ""
    
    # 1. 检查API健康状态
    if (-not (Test-ApiHealth)) {
        Write-Err "请确保后端服务已启动"
        exit 1
    }
    
    # 2. 启动迁移
    Write-Host ""
    if (-not (Start-Migration)) {
        exit 1
    }
    
    # 3. 监控进度
    Write-Host ""
    if (-not (Watch-MigrationProgress)) {
        exit 1
    }
    
    # 4. 验证结果
    Write-Host ""
    $validated = Test-MigrationResult
    if (-not $validated) {
        Write-Warn "建议手动检查数据完整性"
    }
    
    Write-Host ""
    Write-Info "迁移流程完成!"
    Write-Host ""
    Write-Host "后续步骤建议："
    Write-Host "  1. 手动验证向量搜索功能"
    Write-Host "  2. 对比迁移前后的搜索结果"
    Write-Host "  3. 确认无误后可清理Milvus数据"
}

# 执行主流程
Start-MigrationProcess
