package com.echocampus.bot.parser.impl;

import com.echocampus.bot.parser.DocumentParser;
import com.echocampus.bot.parser.dto.DocumentMetadata;
import com.echocampus.bot.parser.exception.DocumentParseException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Excel文档解析器
 * 支持 .xls (Excel 97-2003) 和 .xlsx (Excel 2007+) 格式
 */
@Slf4j
@Component
public class ExcelDocumentParser implements DocumentParser {

    @Override
    public String parse(String filePath) throws DocumentParseException {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            Workbook workbook = createWorkbook(filePath, fis);
            
            try {
                StringBuilder text = new StringBuilder();
                DataFormatter formatter = new DataFormatter();
                
                // 遍历所有工作表
                int numberOfSheets = workbook.getNumberOfSheets();
                for (int i = 0; i < numberOfSheets; i++) {
                    Sheet sheet = workbook.getSheetAt(i);
                    String sheetName = sheet.getSheetName();
                    
                    // 添加工作表名称作为标题
                    text.append("【").append(sheetName).append("】\n");
                    
                    // 遍历所有行
                    for (Row row : sheet) {
                        if (isEmptyRow(row)) {
                            continue;
                        }
                        
                        StringBuilder rowText = new StringBuilder();
                        
                        // 遍历所有单元格
                        for (Cell cell : row) {
                            String cellValue = formatter.formatCellValue(cell);
                            
                            if (cellValue != null && !cellValue.trim().isEmpty()) {
                                if (rowText.length() > 0) {
                                    rowText.append("\t");
                                }
                                rowText.append(cellValue.trim());
                            }
                        }
                        
                        if (rowText.length() > 0) {
                            text.append(rowText.toString()).append("\n");
                        }
                    }
                    
                    // 工作表之间添加空行
                    if (i < numberOfSheets - 1) {
                        text.append("\n");
                    }
                }
                
                String result = text.toString().trim();
                log.info("Excel解析成功: 文件={}, 工作表数={}, 文本长度={}", 
                    filePath, numberOfSheets, result.length());
                
                return result;
                
            } finally {
                workbook.close();
            }
            
        } catch (IOException e) {
            log.error("Excel解析失败: {}", filePath, e);
            throw new DocumentParseException("Excel解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DocumentMetadata getMetadata(String filePath) throws DocumentParseException {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            Workbook workbook = createWorkbook(filePath, fis);
            
            try {
                File file = new File(filePath);
                String title = file.getName();
                
                // 统计工作表数量
                int sheetCount = workbook.getNumberOfSheets();
                
                // 统计总行数
                int totalRows = 0;
                for (int i = 0; i < sheetCount; i++) {
                    Sheet sheet = workbook.getSheetAt(i);
                    totalRows += sheet.getLastRowNum() + 1;
                }
                
                // 获取内容用于计算字符数
                String content = parse(filePath);
                
                return DocumentMetadata.builder()
                    .title(title)
                    .author(null)  // Excel 可能包含作者信息，但需要额外处理
                    .pageCount(sheetCount)  // 使用工作表数量作为页数
                    .fileSize(file.length())
                    .characterCount(content.length())
                    .build();
                    
            } finally {
                workbook.close();
            }
            
        } catch (IOException e) {
            log.error("获取Excel元数据失败: {}", filePath, e);
            throw new DocumentParseException("获取Excel元数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getSupportedTypes() {
        return Arrays.asList("xls", "xlsx");
    }
    
    /**
     * 根据文件扩展名创建对应的 Workbook 对象
     * 
     * @param filePath 文件路径
     * @param fis 文件输入流
     * @return Workbook 对象
     * @throws IOException IO异常
     */
    private Workbook createWorkbook(String filePath, FileInputStream fis) throws IOException {
        String fileName = filePath.toLowerCase();
        
        if (fileName.endsWith(".xls")) {
            // 旧格式 Excel 97-2003 (.xls) - 使用 HSSF
            log.debug("使用 HSSFWorkbook 解析 .xls 格式文件: {}", filePath);
            return new HSSFWorkbook(fis);
        } else if (fileName.endsWith(".xlsx")) {
            // 新格式 Excel 2007+ (.xlsx) - 使用 XSSF
            log.debug("使用 XSSFWorkbook 解析 .xlsx 格式文件: {}", filePath);
            return new XSSFWorkbook(fis);
        } else {
            throw new IOException("不支持的 Excel 文件格式: " + filePath);
        }
    }
    
    /**
     * 检查行是否为空
     * 
     * @param row 行对象
     * @return 是否为空行
     */
    private boolean isEmptyRow(Row row) {
        if (row == null) {
            return true;
        }
        
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String cellValue = cell.toString();
                if (cellValue != null && !cellValue.trim().isEmpty()) {
                    return false;
                }
            }
        }
        
        return true;
    }
}
