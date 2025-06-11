package com.example.contractmanagementsystem.dto;

import com.example.contractmanagementsystem.entity.ContractAnalysis.AnalysisType;
import com.example.contractmanagementsystem.entity.ContractAnalysis.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 合同分析结果DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContractAnalysisResultDto {
    
    private Long id;
    private Long contractId;
    private String contractNumber;
    private String contractName;
    private AnalysisType analysisType;
    private String analysisTypeDesc;
    private RiskLevel riskLevel;
    private String riskLevelDesc;
    private Integer riskScore;
    private String analyzerVersion;
    private LocalDateTime createdAt;
    
    // 分析发现的问题
    private List<AnalysisFinding> findings;
    
    // 推荐措施
    private List<String> recommendations;
    
    // 详细风险信息
    private RiskDetails riskDetails;
    
    /**
     * 分析发现类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisFinding {
        private String category;        // 问题分类
        private String severity;        // 严重程度
        private String title;          // 问题标题
        private String description;    // 问题描述
        private String location;       // 问题位置
        private List<String> suggestions; // 建议
    }
    
    /**
     * 风险详情类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskDetails {
        private Map<String, Integer> categoryScores; // 各类别风险评分
        private List<String> criticalIssues;        // 严重问题列表
        private List<String> warningIssues;         // 警告问题列表
        private List<String> improvementSuggestions; // 改进建议
        private Double overallConfidence;            // 分析置信度
    }
} 