package com.echocampus.bot.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档处理进度DTO
 * 用于向前端推送文档处理的实时进度信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentProgressDTO {

    /**
     * 文档ID
     */
    private Long docId;

    /**
     * 当前处理阶段
     * UPLOADING - 文件上传中
     * PARSING - 文档解析中
     * CHUNKING - 文本切块中
     * EMBEDDING - 向量化处理中
     * STORING - 数据存储中
     * COMPLETED - 处理完成
     * FAILED - 处理失败
     */
    private String stage;

    /**
     * 阶段名称（用于显示）
     */
    private String stageName;

    /**
     * 当前阶段进度百分比 (0-100)
     */
    private Integer progress;

    /**
     * 总体进度百分比 (0-100)
     */
    private Integer totalProgress;

    /**
     * 当前阶段描述信息
     */
    private String message;

    /**
     * 处理详情（如切块数量、向量数量等）
     */
    private String details;

    /**
     * 是否已完成
     */
    private Boolean completed;

    /**
     * 是否失败
     */
    private Boolean failed;

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;

    /**
     * 创建上传阶段进度
     */
    public static DocumentProgressDTO uploading(Long docId, int progress) {
        return DocumentProgressDTO.builder()
                .docId(docId)
                .stage("UPLOADING")
                .stageName("文件上传")
                .progress(progress)
                .totalProgress(calculateTotalProgress("UPLOADING", progress))
                .message("正在上传文件...")
                .completed(false)
                .failed(false)
                .build();
    }

    /**
     * 创建解析阶段进度
     */
    public static DocumentProgressDTO parsing(Long docId, int progress, String details) {
        return DocumentProgressDTO.builder()
                .docId(docId)
                .stage("PARSING")
                .stageName("文档解析")
                .progress(progress)
                .totalProgress(calculateTotalProgress("PARSING", progress))
                .message("正在解析文档内容...")
                .details(details)
                .completed(false)
                .failed(false)
                .build();
    }

    /**
     * 创建切块阶段进度
     */
    public static DocumentProgressDTO chunking(Long docId, int progress, int chunkCount) {
        return DocumentProgressDTO.builder()
                .docId(docId)
                .stage("CHUNKING")
                .stageName("文本切块")
                .progress(progress)
                .totalProgress(calculateTotalProgress("CHUNKING", progress))
                .message("正在进行文本切块...")
                .details(chunkCount > 0 ? "已生成 " + chunkCount + " 个切块" : null)
                .completed(false)
                .failed(false)
                .build();
    }

    /**
     * 创建向量化阶段进度
     */
    public static DocumentProgressDTO embedding(Long docId, int progress, int processedCount, int totalCount) {
        return DocumentProgressDTO.builder()
                .docId(docId)
                .stage("EMBEDDING")
                .stageName("向量化处理")
                .progress(progress)
                .totalProgress(calculateTotalProgress("EMBEDDING", progress))
                .message("正在进行向量化处理...")
                .details(String.format("已处理 %d/%d 个切块", processedCount, totalCount))
                .completed(false)
                .failed(false)
                .build();
    }

    /**
     * 创建存储阶段进度
     */
    public static DocumentProgressDTO storing(Long docId, int progress, String details) {
        return DocumentProgressDTO.builder()
                .docId(docId)
                .stage("STORING")
                .stageName("数据存储")
                .progress(progress)
                .totalProgress(calculateTotalProgress("STORING", progress))
                .message("正在存储向量数据...")
                .details(details)
                .completed(false)
                .failed(false)
                .build();
    }

    /**
     * 创建完成状态
     */
    public static DocumentProgressDTO completed(Long docId, int vectorCount) {
        return DocumentProgressDTO.builder()
                .docId(docId)
                .stage("COMPLETED")
                .stageName("处理完成")
                .progress(100)
                .totalProgress(100)
                .message("文档处理完成！")
                .details("成功生成 " + vectorCount + " 个向量")
                .completed(true)
                .failed(false)
                .build();
    }

    /**
     * 创建失败状态
     */
    public static DocumentProgressDTO failed(Long docId, String stage, String errorMessage) {
        return DocumentProgressDTO.builder()
                .docId(docId)
                .stage("FAILED")
                .stageName("处理失败")
                .progress(0)
                .totalProgress(calculateTotalProgress(stage, 0))
                .message("文档处理失败")
                .errorMessage(errorMessage)
                .completed(false)
                .failed(true)
                .build();
    }

    /**
     * 计算总体进度
     * 各阶段权重: 上传10%, 解析20%, 切块20%, 向量化30%, 存储20%
     */
    private static int calculateTotalProgress(String stage, int stageProgress) {
        int baseProgress = 0;
        int stageWeight = 0;

        switch (stage) {
            case "UPLOADING":
                baseProgress = 0;
                stageWeight = 10;
                break;
            case "PARSING":
                baseProgress = 10;
                stageWeight = 20;
                break;
            case "CHUNKING":
                baseProgress = 30;
                stageWeight = 20;
                break;
            case "EMBEDDING":
                baseProgress = 50;
                stageWeight = 30;
                break;
            case "STORING":
                baseProgress = 80;
                stageWeight = 20;
                break;
            case "COMPLETED":
                return 100;
            case "FAILED":
                return baseProgress;
            default:
                return 0;
        }

        return baseProgress + (stageProgress * stageWeight / 100);
    }
}
