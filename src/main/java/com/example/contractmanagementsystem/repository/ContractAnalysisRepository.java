package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractAnalysis;
import com.example.contractmanagementsystem.entity.ContractAnalysis.AnalysisType;
import com.example.contractmanagementsystem.entity.ContractAnalysis.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractAnalysisRepository extends JpaRepository<ContractAnalysis, Long> {

    /**
     * 根据合同查找所有分析记录
     */
    List<ContractAnalysis> findByContractOrderByCreatedAtDesc(Contract contract);

    /**
     * 根据合同ID和分析类型查找最新的分析记录
     */
    @Query("SELECT ca FROM ContractAnalysis ca WHERE ca.contract.id = :contractId AND ca.analysisType = :analysisType ORDER BY ca.createdAt DESC")
    List<ContractAnalysis> findLatestByContractIdAndType(@Param("contractId") Long contractId, @Param("analysisType") AnalysisType analysisType);

    /**
     * 根据风险等级查找分析记录
     */
    Page<ContractAnalysis> findByRiskLevel(RiskLevel riskLevel, Pageable pageable);

    /**
     * 查找指定时间范围内的高风险合同分析
     */
    @Query("SELECT ca FROM ContractAnalysis ca WHERE ca.riskLevel IN ('HIGH', 'CRITICAL') AND ca.createdAt BETWEEN :startDate AND :endDate ORDER BY ca.riskScore DESC")
    List<ContractAnalysis> findHighRiskAnalysesBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * 统计各风险等级的合同数量
     */
    @Query("SELECT ca.riskLevel, COUNT(DISTINCT ca.contract.id) FROM ContractAnalysis ca WHERE ca.analysisType = :analysisType GROUP BY ca.riskLevel")
    List<Object[]> countContractsByRiskLevel(@Param("analysisType") AnalysisType analysisType);

    /**
     * 查找需要重新分析的合同（分析时间超过指定天数）
     */
    @Query("SELECT DISTINCT ca.contract FROM ContractAnalysis ca WHERE ca.analysisType = :analysisType AND ca.createdAt < :cutoffDate")
    List<Contract> findContractsNeedingReanalysis(@Param("analysisType") AnalysisType analysisType, @Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * 根据合同ID和分析类型查找最新一条分析记录
     */
    Optional<ContractAnalysis> findFirstByContractIdAndAnalysisTypeOrderByCreatedAtDesc(Long contractId, AnalysisType analysisType);

    /**
     * 删除指定合同的所有分析记录
     */
    void deleteByContract(Contract contract);
} 