package com.example.contractmanagementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 合同版本比对结果DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContractComparisonDto {
    
    private Long contractId;
    private String contractNumber;
    private String contractName;
    
    // 版本信息
    private VersionInfo fromVersion;
    private VersionInfo toVersion;
    
    // 比对结果统计
    private ComparisonSummary summary;
    
    // 具体差异列表
    private List<ContentDifference> differences;
    
    // 附件变更
    private List<AttachmentChange> attachmentChanges;
    
    /**
     * 版本信息类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersionInfo {
        private Integer versionNumber;
        private String createdByUsername;
        private LocalDateTime createdAt;
        private String versionDescription;
        private String changeType;
    }
    
    /**
     * 比对摘要类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComparisonSummary {
        private int totalChanges;        // 总变更数
        private int addedLines;          // 新增行数
        private int deletedLines;        // 删除行数
        private int modifiedLines;       // 修改行数
        private double similarityScore;  // 相似度分数 (0-1)
        private List<String> changedSections; // 变更章节
    }
    
    /**
     * 内容差异类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentDifference {
        private String changeType;       // 变更类型：ADD, DELETE, MODIFY
        private String section;          // 变更章节
        private Integer lineNumber;      // 行号
        private String oldContent;       // 原内容
        private String newContent;       // 新内容
        private String description;      // 变更描述
        private String importance;       // 重要性：HIGH, MEDIUM, LOW
    }
    
    /**
     * 附件变更类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentChange {
        private String changeType;       // 变更类型：ADD, DELETE, MODIFY
        private String fileName;         // 文件名
        private String filePath;         // 文件路径
        private Long fileSize;           // 文件大小
        private String description;      // 变更描述
    }
} 