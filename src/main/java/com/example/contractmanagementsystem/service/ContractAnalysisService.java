package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.dto.ContractAnalysisResultDto;
import com.example.contractmanagementsystem.dto.ContractComparisonDto;
import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractAnalysis;
import com.example.contractmanagementsystem.entity.ContractAnalysis.AnalysisType;
import com.example.contractmanagementsystem.entity.ContractAnalysis.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * 合同智能分析服务接口
 * 提供风险识别、条款检查、版本比对等智能分析功能
 */
public interface ContractAnalysisService {

    /**
     * 执行合同风险分析
     * @param contractId 合同ID
     * @return 分析结果
     */
    ContractAnalysisResultDto performRiskAnalysis(Long contractId);

    /**
     * 执行合同条款检查
     * @param contractId 合同ID
     * @return 检查结果
     */
    ContractAnalysisResultDto performClauseCheck(Long contractId);

    /**
     * 执行合同合规检查
     * @param contractId 合同ID
     * @return 检查结果
     */
    ContractAnalysisResultDto performComplianceCheck(Long contractId);

    /**
     * 执行全面合同分析（包含所有类型）
     * @param contractId 合同ID
     * @return 分析结果列表
     */
    List<ContractAnalysisResultDto> performFullAnalysis(Long contractId);

    /**
     * 比对两个合同版本的差异
     * @param contractId 合同ID
     * @param fromVersion 起始版本号
     * @param toVersion 目标版本号
     * @return 比对结果
     */
    ContractComparisonDto compareVersions(Long contractId, Integer fromVersion, Integer toVersion);

    /**
     * 获取合同的分析历史
     * @param contractId 合同ID
     * @param analysisType 分析类型（可选）
     * @return 分析历史列表
     */
    List<ContractAnalysisResultDto> getAnalysisHistory(Long contractId, AnalysisType analysisType);

    /**
     * 获取风险等级统计
     * @return 风险等级统计数据
     */
    Map<RiskLevel, Long> getRiskLevelStatistics();

    /**
     * 查找高风险合同
     * @param pageable 分页参数
     * @return 高风险合同分页列表
     */
    Page<ContractAnalysisResultDto> getHighRiskContracts(Pageable pageable);

    /**
     * 批量分析合同
     * @param contractIds 合同ID列表
     * @param analysisType 分析类型
     * @return 批量分析结果
     */
    List<ContractAnalysisResultDto> batchAnalyze(List<Long> contractIds, AnalysisType analysisType);

    /**
     * 重新分析需要更新的合同
     * @param daysOld 超过多少天的分析需要重新分析
     * @return 重新分析的合同数量
     */
    int reanalyzeOutdatedContracts(int daysOld);

    /**
     * 保存分析结果
     * @param contractAnalysis 分析结果
     * @return 保存的分析结果
     */
    ContractAnalysis saveAnalysisResult(ContractAnalysis contractAnalysis);

    /**
     * 删除合同的所有分析记录
     * @param contractId 合同ID
     */
    void deleteAnalysisResultsByContract(Long contractId);

    /**
     * 获取可分析的合同列表
     * @return 合同列表，每个合同包含id、name等基本信息
     */
    List<Map<String, Object>> getAvailableContracts();

    /**
     * 获取最近的分析记录
     * @param limit 返回记录的最大数量
     * @return 最近的分析记录列表
     */
    List<ContractAnalysisResultDto> getRecentAnalyses(int limit);
} 