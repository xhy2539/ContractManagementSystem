package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.dto.ContractAnalysisResultDto;
import com.example.contractmanagementsystem.dto.ContractComparisonDto;
import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractAnalysis;
import com.example.contractmanagementsystem.entity.ContractAnalysis.AnalysisType;
import com.example.contractmanagementsystem.entity.ContractAnalysis.RiskLevel;
import com.example.contractmanagementsystem.entity.ContractVersion;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.repository.ContractAnalysisRepository;
import com.example.contractmanagementsystem.repository.ContractRepository;
import com.example.contractmanagementsystem.repository.ContractVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 合同智能分析服务实现类
 */
@Service
@Transactional
public class ContractAnalysisServiceImpl implements ContractAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(ContractAnalysisServiceImpl.class);
    private static final String ANALYZER_VERSION = "1.1.0";

    @Autowired
    private ContractAnalysisRepository analysisRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private ContractVersionRepository versionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public ContractAnalysisResultDto performRiskAnalysis(Long contractId) {
        logger.info("开始执行合同风险分析，合同ID: {}", contractId);
        
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ResourceNotFoundException("合同不存在: " + contractId));

        // 执行新的AI风险分析逻辑
        RiskAnalysisResult riskResult = analyzeContractRisksWithAI(contract);
        
        // 保存分析结果
        ContractAnalysis analysis = new ContractAnalysis();
        analysis.setContract(contract);
        analysis.setAnalysisType(AnalysisType.RISK_ANALYSIS);
        analysis.setRiskLevel(riskResult.getRiskLevel());
        analysis.setRiskScore(riskResult.getRiskScore());
        analysis.setAnalyzerVersion(ANALYZER_VERSION);
        
        try {
            analysis.setFindings(objectMapper.writeValueAsString(riskResult.getFindings()));
            analysis.setRecommendations(objectMapper.writeValueAsString(riskResult.getRecommendations()));
        } catch (JsonProcessingException e) {
            logger.error("序列化分析结果失败", e);
            // 即使序列化失败，也应继续，但记录错误
        }
        
        analysis = analysisRepository.save(analysis);
        
        return convertToDto(analysis);
    }

    @Override
    public ContractAnalysisResultDto performClauseCheck(Long contractId) {
        logger.info("开始执行合同条款检查，合同ID: {}", contractId);
        
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ResourceNotFoundException("合同不存在: " + contractId));

        // 执行条款检查逻辑
        ClauseCheckResult clauseResult = checkContractClauses(contract);
        
        // 保存分析结果
        ContractAnalysis analysis = new ContractAnalysis();
        analysis.setContract(contract);
        analysis.setAnalysisType(AnalysisType.CLAUSE_CHECK);
        analysis.setRiskLevel(clauseResult.getRiskLevel());
        analysis.setRiskScore(clauseResult.getRiskScore());
        analysis.setAnalyzerVersion(ANALYZER_VERSION);
        
        try {
            analysis.setFindings(objectMapper.writeValueAsString(clauseResult.getFindings()));
            analysis.setRecommendations(objectMapper.writeValueAsString(clauseResult.getRecommendations()));
        } catch (JsonProcessingException e) {
            logger.error("序列化条款检查结果失败", e);
        }
        
        analysis = analysisRepository.save(analysis);
        
        return convertToDto(analysis, clauseResult.getFindings(), clauseResult.getRecommendations());
    }

    @Override
    public ContractAnalysisResultDto performComplianceCheck(Long contractId) {
        logger.info("开始执行合同合规检查，合同ID: {}", contractId);
        
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ResourceNotFoundException("合同不存在: " + contractId));

        // 执行合规检查逻辑
        ComplianceCheckResult complianceResult = checkContractCompliance(contract);
        
        // 保存分析结果
        ContractAnalysis analysis = new ContractAnalysis();
        analysis.setContract(contract);
        analysis.setAnalysisType(AnalysisType.COMPLIANCE_CHECK);
        analysis.setRiskLevel(complianceResult.getRiskLevel());
        analysis.setRiskScore(complianceResult.getRiskScore());
        analysis.setAnalyzerVersion(ANALYZER_VERSION);
        
        try {
            analysis.setFindings(objectMapper.writeValueAsString(complianceResult.getFindings()));
            analysis.setRecommendations(objectMapper.writeValueAsString(complianceResult.getRecommendations()));
        } catch (JsonProcessingException e) {
            logger.error("序列化合规检查结果失败", e);
        }
        
        analysis = analysisRepository.save(analysis);
        
        return convertToDto(analysis, complianceResult.getFindings(), complianceResult.getRecommendations());
    }

    @Override
    public List<ContractAnalysisResultDto> performFullAnalysis(Long contractId) {
        logger.info("开始执行全面合同分析，合同ID: {}", contractId);
        
        List<ContractAnalysisResultDto> results = new ArrayList<>();
        
        // 执行所有类型的分析
        results.add(performRiskAnalysis(contractId));
        results.add(performClauseCheck(contractId));
        results.add(performComplianceCheck(contractId));
        
        return results;
    }

    @Override
    public ContractComparisonDto compareVersions(Long contractId, Integer fromVersion, Integer toVersion) {
        logger.info("开始比对合同版本，合同ID: {}, 从版本: {}, 到版本: {}", contractId, fromVersion, toVersion);
        
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ResourceNotFoundException("合同不存在: " + contractId));

        ContractVersion fromVer = versionRepository.findByContractIdAndVersionNumber(contractId, fromVersion)
            .orElseThrow(() -> new ResourceNotFoundException("源版本不存在: " + fromVersion));
            
        ContractVersion toVer = versionRepository.findByContractIdAndVersionNumber(contractId, toVersion)
            .orElseThrow(() -> new ResourceNotFoundException("目标版本不存在: " + toVersion));

        return compareContractVersions(contract, fromVer, toVer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractAnalysisResultDto> getAnalysisHistory(Long contractId, AnalysisType analysisType) {
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ResourceNotFoundException("合同不存在: " + contractId));

        List<ContractAnalysis> analyses;
        if (analysisType != null) {
            analyses = analysisRepository.findLatestByContractIdAndType(contractId, analysisType);
        } else {
            analyses = analysisRepository.findByContractOrderByCreatedAtDesc(contract);
        }

        return analyses.stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<RiskLevel, Long> getRiskLevelStatistics() {
        List<Object[]> results = analysisRepository.countContractsByRiskLevel(AnalysisType.RISK_ANALYSIS);
        Map<RiskLevel, Long> statistics = new HashMap<>();
        
        for (RiskLevel level : RiskLevel.values()) {
            statistics.put(level, 0L);
        }
        
        for (Object[] result : results) {
            if (result[0] instanceof RiskLevel && result[1] instanceof Long) {
                statistics.put((RiskLevel) result[0], (Long) result[1]);
            }
        }
        
        return statistics;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContractAnalysisResultDto> getHighRiskContracts(Pageable pageable) {
        Page<ContractAnalysis> analyses = analysisRepository.findByRiskLevel(RiskLevel.HIGH, pageable);
        List<ContractAnalysisResultDto> dtos = analyses.getContent().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
        
        return new PageImpl<>(dtos, pageable, analyses.getTotalElements());
    }

    @Override
    public List<ContractAnalysisResultDto> batchAnalyze(List<Long> contractIds, AnalysisType analysisType) {
        List<ContractAnalysisResultDto> results = new ArrayList<>();
        
        for (Long contractId : contractIds) {
            try {
                ContractAnalysisResultDto result;
                switch (analysisType) {
                    case RISK_ANALYSIS:
                        result = performRiskAnalysis(contractId);
                        break;
                    case CLAUSE_CHECK:
                        result = performClauseCheck(contractId);
                        break;
                    case COMPLIANCE_CHECK:
                        result = performComplianceCheck(contractId);
                        break;
                    default:
                        continue;
                }
                results.add(result);
            } catch (Exception e) {
                logger.error("批量分析合同失败，合同ID: {}", contractId, e);
            }
        }
        
        return results;
    }

    @Override
    public int reanalyzeOutdatedContracts(int daysOld) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);
        List<Contract> contracts = analysisRepository.findContractsNeedingReanalysis(AnalysisType.RISK_ANALYSIS, cutoffDate);
        
        int reanalyzedCount = 0;
        for (Contract contract : contracts) {
            try {
                performRiskAnalysis(contract.getId());
                reanalyzedCount++;
            } catch (Exception e) {
                logger.error("重新分析合同失败，合同ID: {}", contract.getId(), e);
            }
        }
        
        return reanalyzedCount;
    }

    @Override
    public ContractAnalysis saveAnalysisResult(ContractAnalysis contractAnalysis) {
        return analysisRepository.save(contractAnalysis);
    }

    @Override
    public void deleteAnalysisResultsByContract(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ResourceNotFoundException("合同不存在: " + contractId));
        analysisRepository.deleteByContract(contract);
    }

    // 私有方法：执行风险分析
    private RiskAnalysisResult analyzeContractRisksWithAI(Contract contract) {
        String content = contract.getContent() != null ? contract.getContent().toLowerCase() : "";
        List<ContractAnalysisResultDto.AnalysisFinding> findings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        int riskScore = 0;

        // 1. 检查缺失的关键条款
        riskScore += checkMissingClauses(content, findings, recommendations);

        // 2. 检查模糊或高风险的关键词
        riskScore += checkRiskyKeywords(content, findings, recommendations);

        // 3. 检查付款条款
        riskScore += checkPaymentTerms(content, findings, recommendations);

        // 4. 检查合同期限
        riskScore += checkContractTerm(contract, content, findings, recommendations);

        // 标准化风险分数 (0-100)
        riskScore = Math.min(100, Math.max(0, riskScore));

        RiskLevel riskLevel = calculateRiskLevel(riskScore);
        
        if (findings.isEmpty()) {
            findings.add(new ContractAnalysisResultDto.AnalysisFinding(
                "总体评估", "低", "未发现明显风险点", "合同文本初步审查未发现高风险关键词或缺失关键条款。", "全文", List.of("建议由法务人员进行最终审核。")));
        }

        return new RiskAnalysisResult(riskLevel, riskScore, findings, recommendations);
    }

    private int checkMissingClauses(String content, List<ContractAnalysisResultDto.AnalysisFinding> findings, List<String> recommendations) {
        int score = 0;
        Map<String, String[]> missingClausesKeywords = Map.of(
            "保密条款", new String[]{"confidential", "保密"},
            "争议解决", new String[]{"dispute", "争议", "仲裁", "诉讼"},
            "不可抗力", new String[]{"force majeure", "不可抗力"},
            "违约责任", new String[]{"breach", "default", "违约"}
        );

        for (Map.Entry<String, String[]> entry : missingClausesKeywords.entrySet()) {
            boolean found = false;
            for (String keyword : entry.getValue()) {
                if (content.contains(keyword)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                score += 20; // 缺失关键条款，风险分增加较多
                findings.add(new ContractAnalysisResultDto.AnalysisFinding("条款缺失", "高", "可能缺失 " + entry.getKey(), "合同中未明确提及" + entry.getKey() + "，可能导致未来出现争议时权责不清。", "全文", List.of("建议补充完整的" + entry.getKey() + "。")));
                recommendations.add("强烈建议补充关于\"" + entry.getKey() + "\"的详细条款。");
            }
        }
        return score;
    }

    private int checkRiskyKeywords(String content, List<ContractAnalysisResultDto.AnalysisFinding> findings, List<String> recommendations) {
        int score = 0;
        Map<String, Integer> riskyKeywords = Map.ofEntries(
            Map.entry("无限责任", 25),
            Map.entry("unlimited liability", 25),
            Map.entry("单方面终止", 20),
            Map.entry("unilateral termination", 20),
            Map.entry("赔偿", 15),
            Map.entry("indemnify", 15),
            Map.entry("indemnity", 15),
            Map.entry("自动续约", 10),
            Map.entry("automatic renewal", 10),
            Map.entry("排除", 10),
            Map.entry("exclude", 10),
            Map.entry("disclaim", 10)
        );

        for (Map.Entry<String, Integer> entry : riskyKeywords.entrySet()) {
            if (content.contains(entry.getKey())) {
                score += entry.getValue();
                findings.add(new ContractAnalysisResultDto.AnalysisFinding("高风险词汇", "中", "发现高风险关键词: " + entry.getKey(), "包含\"" + entry.getKey() + "\"可能对本方不利，需仔细审查相关条款。", "全文", List.of("请法务人员重点审查包含\"" + entry.getKey() + "\"的条款，评估其潜在影响。")));
                recommendations.add("重点审查\"" + entry.getKey() + "\"相关条款，确保其公平合理。");
            }
        }
        return score;
    }

    private int checkPaymentTerms(String content, List<ContractAnalysisResultDto.AnalysisFinding> findings, List<String> recommendations) {
        int score = 0;
        if (!content.contains("支付") && !content.contains("payment")) {
            score += 15;
            findings.add(new ContractAnalysisResultDto.AnalysisFinding("条款缺失", "高", "支付条款不明确", "合同未明确提及支付金额、时间和方式。", "全文", List.of("建议添加详细的支付条款，包括金额、币种、支付节点、支付方式等。")));
            recommendations.add("添加明确的支付条款。");
        } else {
            if (content.contains("预付") || content.contains("prepayment")) {
                score += 5;
                findings.add(new ContractAnalysisResultDto.AnalysisFinding("付款方式", "低", "包含预付款项", "合同中涉及预付款，请注意相关风险。", "全文", null));
                recommendations.add("评估预付款的必要性和风险。");
            }
        }
        return score;
    }

    private int checkContractTerm(Contract contract, String content, List<ContractAnalysisResultDto.AnalysisFinding> findings, List<String> recommendations) {
        if (contract.getEndDate() != null && contract.getEndDate().isBefore(LocalDate.now())) {
            findings.add(new ContractAnalysisResultDto.AnalysisFinding("合同状态", "高", "合同已过期", "根据系统记录，该合同已于 " + contract.getEndDate() + " 到期。", "合同期限", List.of("请确认合同是否需要续签或归档。")));
            return 20;
        }
        if (content.contains("无固定期限") || content.contains("non-fixed term")) {
            findings.add(new ContractAnalysisResultDto.AnalysisFinding("合同期限", "中", "无固定期限合同", "此类合同可能导致长期义务，需谨慎评估。", "合同期限", List.of("明确合同的终止条件，避免无限期责任。")));
            recommendations.add("为无固定期限的合同设定明确的终止条件。");
            return 15;
        }
        return 0;
    }

    private RiskLevel calculateRiskLevel(int riskScore) {
        if (riskScore >= 75) return RiskLevel.CRITICAL;
        if (riskScore >= 50) return RiskLevel.HIGH;
        if (riskScore >= 25) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    // 私有方法：条款检查
    private ClauseCheckResult checkContractClauses(Contract contract) {
        // ... 模拟实现 ...
        List<ContractAnalysisResultDto.AnalysisFinding> findings = new ArrayList<>();
        findings.add(new ContractAnalysisResultDto.AnalysisFinding("条款检查", "低", "条款完整性", "核心条款（定义、双方权利义务、价格）基本完整。", "全文", null));
        return new ClauseCheckResult(RiskLevel.LOW, 10, findings, List.of("建议法务复核。"));
    }

    // 私有方法：合规检查
    private ComplianceCheckResult checkContractCompliance(Contract contract) {
        // ... 模拟实现 ...
        List<ContractAnalysisResultDto.AnalysisFinding> findings = new ArrayList<>();
        findings.add(new ContractAnalysisResultDto.AnalysisFinding("合规检查", "低", "行业法规", "未发现明显违反已知行业法规的内容。", "全文", null));
        return new ComplianceCheckResult(RiskLevel.LOW, 5, findings, List.of("建议对照最新的法律法规进行审查。"));
    }

    // 私有方法：版本比对
    private ContractComparisonDto compareContractVersions(Contract contract, ContractVersion fromVer, ContractVersion toVer) {
        // ... 模拟实现 ...
        return new ContractComparisonDto();
    }

    // 私有方法：转换为DTO
    private ContractAnalysisResultDto convertToDto(ContractAnalysis analysis) {
        try {
            List<ContractAnalysisResultDto.AnalysisFinding> findings = StringUtils.hasText(analysis.getFindings())
                ? objectMapper.readValue(analysis.getFindings(), new TypeReference<>() {})
                : new ArrayList<>();

            List<String> recommendations = StringUtils.hasText(analysis.getRecommendations())
                ? objectMapper.readValue(analysis.getRecommendations(), new TypeReference<>() {})
                : new ArrayList<>();
            
            return convertToDto(analysis, findings, recommendations);
        } catch (JsonProcessingException e) {
            logger.error("反序列化分析结果失败，分析ID: {}", analysis.getId(), e);
            // 返回一个包含错误信息的DTO
            ContractAnalysisResultDto errorDto = new ContractAnalysisResultDto();
            errorDto.setId(analysis.getId());
            errorDto.setContractId(analysis.getContract().getId());
            errorDto.setContractName(analysis.getContract().getContractName());
            errorDto.setFindings(List.of(new ContractAnalysisResultDto.AnalysisFinding("错误", "高", "数据解析失败", "无法解析存储的分析结果。", null, null)));
            errorDto.setRecommendations(List.of("请联系管理员检查系统日志。"));
            return errorDto;
        }
    }

    private ContractAnalysisResultDto convertToDto(ContractAnalysis analysis, 
                                                  List<ContractAnalysisResultDto.AnalysisFinding> findings,
                                                  List<String> recommendations) {
        ContractAnalysisResultDto dto = new ContractAnalysisResultDto();
        dto.setId(analysis.getId());
        dto.setContractId(analysis.getContract().getId());
        dto.setContractNumber(analysis.getContract().getContractNumber());
        dto.setContractName(analysis.getContract().getContractName());
        dto.setAnalysisType(analysis.getAnalysisType());
        dto.setAnalysisTypeDesc(analysis.getAnalysisType().getDescription());
        dto.setRiskLevel(analysis.getRiskLevel());
        dto.setRiskLevelDesc(analysis.getRiskLevel().getDescription());
        dto.setRiskScore(analysis.getRiskScore());
        dto.setAnalyzerVersion(analysis.getAnalyzerVersion());
        dto.setCreatedAt(analysis.getCreatedAt());
        dto.setFindings(findings);
        dto.setRecommendations(recommendations);
        
        return dto;
    }

    // 内部类：风险分析结果
    private static class RiskAnalysisResult {
        private final RiskLevel riskLevel;
        private final Integer riskScore;
        private final List<ContractAnalysisResultDto.AnalysisFinding> findings;
        private final List<String> recommendations;

        public RiskAnalysisResult(RiskLevel riskLevel, Integer riskScore, 
                                 List<ContractAnalysisResultDto.AnalysisFinding> findings, 
                                 List<String> recommendations) {
            this.riskLevel = riskLevel;
            this.riskScore = riskScore;
            this.findings = findings;
            this.recommendations = recommendations;
        }

        public RiskLevel getRiskLevel() { return riskLevel; }
        public Integer getRiskScore() { return riskScore; }
        public List<ContractAnalysisResultDto.AnalysisFinding> getFindings() { return findings; }
        public List<String> getRecommendations() { return recommendations; }
    }

    // 内部类：条款检查结果
    private static class ClauseCheckResult {
        private final RiskLevel riskLevel;
        private final Integer riskScore;
        private final List<ContractAnalysisResultDto.AnalysisFinding> findings;
        private final List<String> recommendations;

        public ClauseCheckResult(RiskLevel riskLevel, Integer riskScore, 
                                List<ContractAnalysisResultDto.AnalysisFinding> findings, 
                                List<String> recommendations) {
            this.riskLevel = riskLevel;
            this.riskScore = riskScore;
            this.findings = findings;
            this.recommendations = recommendations;
        }

        public RiskLevel getRiskLevel() { return riskLevel; }
        public Integer getRiskScore() { return riskScore; }
        public List<ContractAnalysisResultDto.AnalysisFinding> getFindings() { return findings; }
        public List<String> getRecommendations() { return recommendations; }
    }

    // 内部类：合规检查结果
    private static class ComplianceCheckResult {
        private final RiskLevel riskLevel;
        private final Integer riskScore;
        private final List<ContractAnalysisResultDto.AnalysisFinding> findings;
        private final List<String> recommendations;

        public ComplianceCheckResult(RiskLevel riskLevel, Integer riskScore, 
                                    List<ContractAnalysisResultDto.AnalysisFinding> findings, 
                                    List<String> recommendations) {
            this.riskLevel = riskLevel;
            this.riskScore = riskScore;
            this.findings = findings;
            this.recommendations = recommendations;
        }

        public RiskLevel getRiskLevel() { return riskLevel; }
        public Integer getRiskScore() { return riskScore; }
        public List<ContractAnalysisResultDto.AnalysisFinding> getFindings() { return findings; }
        public List<String> getRecommendations() { return recommendations; }
    }
} 