package com.echocampus.bot.parser.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 文档元数据
 */
@Data
@Builder
public class DocumentMetadata {
    
    /** 文档标题 */
    private String title;
    
    /** 作者 */
    private String author;
    
    /** 主题 */
    private String subject;
    
    /** 关键词 */
    private String keywords;
    
    /** 页数 (PDF/Word) */
    private Integer pageCount;
    
    /** 幻灯片数 (PPT) */
    private Integer slideCount;
    
    /** 文件大小(字节) */
    private Long fileSize;
    
    /** 创建日期 */
    private String createdDate;
    
    /** 修改日期 */
    private String modifiedDate;
    
    /** 字符数 */
    private Integer characterCount;
}
