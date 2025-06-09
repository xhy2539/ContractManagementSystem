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
    private static final String ANALYZER_VERSION = "1.0.0";

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

        // 执行风险分析逻辑
        RiskAnalysisResult riskResult = analyzeContractRisks(contract);
        
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
        }
        
        analysis = analysisRepository.save(analysis);
        
        return convertToDto(analysis, riskResult.getFindings(), riskResult.getRecommendations());
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
    private RiskAnalysisResult analyzeContractRisks(Contract contract) {
        List<ContractAnalysisResultDto.AnalysisFinding> findings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        int totalScore = 0;
        
        String content = contract.getContent();
        if (!StringUtils.hasText(content)) {
            content = "";
        }
        
        // 1. 检查关键条款缺失
        findings.addAll(checkMissingKeyTerms(content));
        
        // 2. 检查风险条款
        findings.addAll(checkRiskyClauses(content));
        
        // 3. 检查日期相关风险
        findings.addAll(checkDateRisks(contract));
        
        // 4. 检查金额和支付条款
        findings.addAll(checkPaymentTerms(content));
        
        // 计算风险评分
        for (ContractAnalysisResultDto.AnalysisFinding finding : findings) {
            switch (finding.getSeverity()) {
                case "CRITICAL":
                    totalScore += 25;
                    break;
                case "HIGH":
                    totalScore += 15;
                    break;
                case "MEDIUM":
                    totalScore += 10;
                    break;
                case "LOW":
                    totalScore += 5;
                    break;
            }
        }
        
        // 生成建议
        recommendations.addAll(generateRiskRecommendations(findings));
        
        RiskLevel riskLevel = determineRiskLevel(totalScore);
        
        return new RiskAnalysisResult(riskLevel, totalScore, findings, recommendations);
    }

    // 私有方法：检查关键条款缺失
    private List<ContractAnalysisResultDto.AnalysisFinding> checkMissingKeyTerms(String content) {
        List<ContractAnalysisResultDto.AnalysisFinding> findings = new ArrayList<>();
        
        String[] keyTerms = {
            "违约责任", "争议解决", "保密条款", "知识产权", "不可抗力",
            "合同终止", "付款方式", "交付条件", "验收标准", "法律适用"
        };
        
        for (String term : keyTerms) {
            if (!content.contains(term)) {
                ContractAnalysisResultDto.AnalysisFinding finding = new ContractAnalysisResultDto.AnalysisFinding();
                finding.setCategory("关键条款缺失");
                finding.setSeverity("MEDIUM");
                finding.setTitle("缺少" + term + "条款");
                finding.setDescription("合同中未发现" + term + "相关条款，可能存在法律风险");
                finding.setLocation("全文");
                finding.setSuggestions(Arrays.asList("建议添加" + term + "相关条款", "咨询法务部门"));
                findings.add(finding);
            }
        }
        
        return findings;
    }

    // 私有方法：检查风险条款
    private List<ContractAnalysisResultDto.AnalysisFinding> checkRiskyClauses(String content) {
        List<ContractAnalysisResultDto.AnalysisFinding> findings = new ArrayList<>();
        
        // 检查风险关键词
        Map<String, String> riskyPatterns = new HashMap<>();
        riskyPatterns.put("无限责任", "CRITICAL");
        riskyPatterns.put("连带责任", "HIGH");
        riskyPatterns.put("不可撤销", "HIGH");
        riskyPatterns.put("无条件", "MEDIUM");
        riskyPatterns.put("永久有效", "HIGH");
        
        for (Map.Entry<String, String> entry : riskyPatterns.entrySet()) {
            if (content.contains(entry.getKey())) {
                ContractAnalysisResultDto.AnalysisFinding finding = new ContractAnalysisResultDto.AnalysisFinding();
                finding.setCategory("风险条款");
                finding.setSeverity(entry.getValue());
                finding.setTitle("发现风险条款：" + entry.getKey());
                finding.setDescription("合同中包含可能带来法律风险的条款");
                finding.setLocation("合同正文");
                finding.setSuggestions(Arrays.asList("仔细评估风险", "考虑修改条款", "寻求法律建议"));
                findings.add(finding);
            }
        }
        
        return findings;
    }

    // 私有方法：检查日期相关风险
    private List<ContractAnalysisResultDto.AnalysisFinding> checkDateRisks(Contract contract) {
        List<ContractAnalysisResultDto.AnalysisFinding> findings = new ArrayList<>();
        
        // 检查合同期限是否过短或过长
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(contract.getStartDate(), contract.getEndDate());
        
        if (daysBetween < 30) {
            ContractAnalysisResultDto.AnalysisFinding finding = new ContractAnalysisResultDto.AnalysisFinding();
            finding.setCategory("合同期限");
            finding.setSeverity("MEDIUM");
            finding.setTitle("合同期限过短");
            finding.setDescription("合同期限少于30天，可能影响项目执行");
            finding.setLocation("合同期限");
            finding.setSuggestions(Arrays.asList("确认期限是否合理", "考虑延长合同期限"));
            findings.add(finding);
        } else if (daysBetween > 3650) { // 超过10年
            ContractAnalysisResultDto.AnalysisFinding finding = new ContractAnalysisResultDto.AnalysisFinding();
            finding.setCategory("合同期限");
            finding.setSeverity("LOW");
            finding.setTitle("合同期限过长");
            finding.setDescription("合同期限超过10年，建议定期审查");
            finding.setLocation("合同期限");
            finding.setSuggestions(Arrays.asList("设置阶段性审查节点", "考虑增加调整条款"));
            findings.add(finding);
        }
        
        return findings;
    }

    // 私有方法：检查支付条款
    private List<ContractAnalysisResultDto.AnalysisFinding> checkPaymentTerms(String content) {
        List<ContractAnalysisResultDto.AnalysisFinding> findings = new ArrayList<>();
        
        // 检查是否包含明确的支付条款
        if (!content.contains("支付") && !content.contains("付款") && !content.contains("费用")) {
            ContractAnalysisResultDto.AnalysisFinding finding = new ContractAnalysisResultDto.AnalysisFinding();
            finding.setCategory("支付条款");
            finding.setSeverity("HIGH");
            finding.setTitle("缺少支付条款");
            finding.setDescription("合同中未发现明确的支付相关条款");
            finding.setLocation("全文");
            finding.setSuggestions(Arrays.asList("添加详细的支付条款", "明确支付时间和方式"));
            findings.add(finding);
        }
        
        return findings;
    }

    // 私有方法：生成风险建议
    private List<String> generateRiskRecommendations(List<ContractAnalysisResultDto.AnalysisFinding> findings) {
        List<String> recommendations = new ArrayList<>();
        
        long criticalCount = findings.stream().filter(f -> "CRITICAL".equals(f.getSeverity())).count();
        long highCount = findings.stream().filter(f -> "HIGH".equals(f.getSeverity())).count();
        
        if (criticalCount > 0) {
            recommendations.add("发现" + criticalCount + "个严重风险项，建议立即处理");
            recommendations.add("强烈建议咨询专业法务人员");
        }
        
        if (highCount > 0) {
            recommendations.add("发现" + highCount + "个高风险项，建议重点关注");
        }
        
        recommendations.add("建议在签订前进行全面的法律审查");
        recommendations.add("建议建立风险监控机制");
        
        return recommendations;
    }

    // 私有方法：确定风险等级
    private RiskLevel determineRiskLevel(int totalScore) {
        if (totalScore >= 75) {
            return RiskLevel.CRITICAL;
        } else if (totalScore >= 50) {
            return RiskLevel.HIGH;
        } else if (totalScore >= 25) {
            return RiskLevel.MEDIUM;
        } else {
            return RiskLevel.LOW;
        }
    }

    // 私有方法：条款检查
    private ClauseCheckResult checkContractClauses(Contract contract) {
        // 简化的条款检查逻辑
        List<ContractAnalysisResultDto.AnalysisFinding> findings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        
        // 这里可以实现更复杂的条款检查逻辑
        // 目前使用简化版本
        findings.addAll(checkMissingKeyTerms(contract.getContent()));
        
        int score = findings.size() * 10;
        RiskLevel riskLevel = determineRiskLevel(score);
        
        recommendations.add("建议完善缺失的关键条款");
        recommendations.add("建议定期审查合同条款的有效性");
        
        return new ClauseCheckResult(riskLevel, score, findings, recommendations);
    }

    // 私有方法：合规检查
    private ComplianceCheckResult checkContractCompliance(Contract contract) {
        // 简化的合规检查逻辑
        List<ContractAnalysisResultDto.AnalysisFinding> findings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        
        // 这里可以实现更复杂的合规检查逻辑
        findings.addAll(checkDateRisks(contract));
        
        int score = findings.size() * 15;
        RiskLevel riskLevel = determineRiskLevel(score);
        
        recommendations.add("建议确保合同符合相关法律法规");
        recommendations.add("建议定期更新合规要求");
        
        return new ComplianceCheckResult(riskLevel, score, findings, recommendations);
    }

    // 私有方法：版本比对
    private ContractComparisonDto compareContractVersions(Contract contract, ContractVersion fromVer, ContractVersion toVer) {
        ContractComparisonDto comparison = new ContractComparisonDto();
        comparison.setContractId(contract.getId());
        comparison.setContractNumber(contract.getContractNumber());
        comparison.setContractName(contract.getContractName());
        
        // 设置版本信息
        ContractComparisonDto.VersionInfo fromInfo = new ContractComparisonDto.VersionInfo();
        fromInfo.setVersionNumber(fromVer.getVersionNumber());
        fromInfo.setCreatedByUsername(fromVer.getCreatedByUsername());
        fromInfo.setCreatedAt(fromVer.getCreatedAt());
        fromInfo.setVersionDescription(fromVer.getVersionDescription());
        fromInfo.setChangeType(fromVer.getChangeType().getDescription());
        comparison.setFromVersion(fromInfo);
        
        ContractComparisonDto.VersionInfo toInfo = new ContractComparisonDto.VersionInfo();
        toInfo.setVersionNumber(toVer.getVersionNumber());
        toInfo.setCreatedByUsername(toVer.getCreatedByUsername());
        toInfo.setCreatedAt(toVer.getCreatedAt());
        toInfo.setVersionDescription(toVer.getVersionDescription());
        toInfo.setChangeType(toVer.getChangeType().getDescription());
        comparison.setToVersion(toInfo);
        
        // 简化的差异分析
        List<ContractComparisonDto.ContentDifference> differences = compareContent(fromVer.getContent(), toVer.getContent());
        comparison.setDifferences(differences);
        
        // 设置摘要
        ContractComparisonDto.ComparisonSummary summary = new ContractComparisonDto.ComparisonSummary();
        summary.setTotalChanges(differences.size());
        summary.setModifiedLines(differences.size());
        summary.setSimilarityScore(calculateSimilarity(fromVer.getContent(), toVer.getContent()));
        comparison.setSummary(summary);
        
        return comparison;
    }

    // 私有方法：比较内容
    private List<ContractComparisonDto.ContentDifference> compareContent(String oldContent, String newContent) {
        List<ContractComparisonDto.ContentDifference> differences = new ArrayList<>();
        
        if (oldContent == null) oldContent = "";
        if (newContent == null) newContent = "";
        
        String[] oldLines = oldContent.split("\n");
        String[] newLines = newContent.split("\n");
        
        // 简化的行级别比较
        int maxLines = Math.max(oldLines.length, newLines.length);
        for (int i = 0; i < maxLines; i++) {
            String oldLine = i < oldLines.length ? oldLines[i] : "";
            String newLine = i < newLines.length ? newLines[i] : "";
            
            if (!oldLine.equals(newLine)) {
                ContractComparisonDto.ContentDifference diff = new ContractComparisonDto.ContentDifference();
                diff.setChangeType("MODIFY");
                diff.setLineNumber(i + 1);
                diff.setOldContent(oldLine);
                diff.setNewContent(newLine);
                diff.setDescription("第" + (i + 1) + "行内容发生变更");
                diff.setImportance("MEDIUM");
                differences.add(diff);
            }
        }
        
        return differences;
    }

    // 私有方法：计算相似度
    private double calculateSimilarity(String text1, String text2) {
        if (text1 == null) text1 = "";
        if (text2 == null) text2 = "";
        
        if (text1.equals(text2)) {
            return 1.0;
        }
        
        // 简化的相似度计算
        int maxLength = Math.max(text1.length(), text2.length());
        if (maxLength == 0) {
            return 1.0;
        }
        
        int commonChars = 0;
        int minLength = Math.min(text1.length(), text2.length());
        for (int i = 0; i < minLength; i++) {
            if (text1.charAt(i) == text2.charAt(i)) {
                commonChars++;
            }
        }
        
        return (double) commonChars / maxLength;
    }

    // 私有方法：转换为DTO
    private ContractAnalysisResultDto convertToDto(ContractAnalysis analysis) {
        List<ContractAnalysisResultDto.AnalysisFinding> findings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        
        try {
            if (StringUtils.hasText(analysis.getFindings())) {
                findings = objectMapper.readValue(analysis.getFindings(), 
                    new TypeReference<List<ContractAnalysisResultDto.AnalysisFinding>>() {});
            }
            if (StringUtils.hasText(analysis.getRecommendations())) {
                recommendations = objectMapper.readValue(analysis.getRecommendations(), 
                    new TypeReference<List<String>>() {});
            }
        } catch (JsonProcessingException e) {
            logger.error("反序列化分析结果失败", e);
        }
        
        return convertToDto(analysis, findings, recommendations);
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