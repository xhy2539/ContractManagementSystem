package com.example.contractmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 合同智能分析实体
 * 存储合同的风险分析、关键条款检查等智能分析结果
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "contract_analyses", indexes = {
    @Index(name = "idx_analysis_contract", columnList = "contract_id"),
    @Index(name = "idx_analysis_type", columnList = "analysis_type"),
    @Index(name = "idx_analysis_risk_level", columnList = "risk_level"),
    @Index(name = "idx_analysis_created_at", columnList = "created_at")
})
public class ContractAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type", nullable = false, length = 30)
    private AnalysisType analysisType;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel;

    @Column(name = "risk_score")
    private Integer riskScore; // 风险评分 0-100

    @Lob
    @Column(name = "findings", columnDefinition = "TEXT")
    private String findings; // JSON格式存储检查结果

    @Lob
    @Column(name = "recommendations", columnDefinition = "TEXT")
    private String recommendations; // 建议措施

    @Column(name = "analyzer_version", length = 20)
    private String analyzerVersion; // 分析器版本

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 分析类型枚举
     */
    public enum AnalysisType {
        RISK_ANALYSIS("风险分析"),
        CLAUSE_CHECK("条款检查"),
        LEGAL_REVIEW("法律审查"),
        FINANCIAL_ANALYSIS("财务分析"),
        COMPLIANCE_CHECK("合规检查");

        private final String description;

        AnalysisType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 风险等级枚举
     */
    public enum RiskLevel {
        LOW("低风险"),
        MEDIUM("中风险"),
        HIGH("高风险"),
        CRITICAL("严重风险");

        private final String description;

        RiskLevel(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
} 