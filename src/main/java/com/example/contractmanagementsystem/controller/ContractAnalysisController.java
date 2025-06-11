package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.ContractAnalysisResultDto;
import com.example.contractmanagementsystem.dto.ContractComparisonDto;
import com.example.contractmanagementsystem.entity.ContractAnalysis.AnalysisType;
import com.example.contractmanagementsystem.entity.ContractAnalysis.RiskLevel;
import com.example.contractmanagementsystem.service.ContractAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 合同智能分析控制器
 */
@Controller
@RequestMapping("/contract-analysis")
public class ContractAnalysisController {

    private static final Logger logger = LoggerFactory.getLogger(ContractAnalysisController.class);

    @Autowired
    private ContractAnalysisService analysisService;

    /**
     * 智能分析主页
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRACT_OPERATOR')")
    public String analysisHomePage(Model model) {
        try {
            // 获取风险等级统计
            Map<RiskLevel, Long> riskStats = analysisService.getRiskLevelStatistics();
            model.addAttribute("riskStats", riskStats);
            
            logger.info("智能分析主页加载成功，风险统计: {}", riskStats);
            return "analysis/analysis-home";
        } catch (Exception e) {
            logger.error("智能分析主页加载失败", e);
            model.addAttribute("error", e.getMessage());
            return "analysis/analysis-home";
        }
    }

    /**
     * 执行单个合同的风险分析
     */
    @PostMapping("/risk-analysis/{contractId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRACT_OPERATOR')")
    public ResponseEntity<ContractAnalysisResultDto> performRiskAnalysis(@PathVariable Long contractId) {
        logger.info("执行合同风险分析，合同ID: {}", contractId);
        
        try {
            ContractAnalysisResultDto result = analysisService.performRiskAnalysis(contractId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("风险分析执行失败，合同ID: {}", contractId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 执行单个合同的条款检查
     */
    @PostMapping("/clause-check/{contractId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRACT_OPERATOR')")
    public ResponseEntity<ContractAnalysisResultDto> performClauseCheck(@PathVariable Long contractId) {
        logger.info("执行合同条款检查，合同ID: {}", contractId);
        
        try {
            ContractAnalysisResultDto result = analysisService.performClauseCheck(contractId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("条款检查执行失败，合同ID: {}", contractId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 执行单个合同的合规检查
     */
    @PostMapping("/compliance-check/{contractId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRACT_OPERATOR')")
    public ResponseEntity<ContractAnalysisResultDto> performComplianceCheck(@PathVariable Long contractId) {
        logger.info("执行合同合规检查，合同ID: {}", contractId);
        
        try {
            ContractAnalysisResultDto result = analysisService.performComplianceCheck(contractId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("合规检查执行失败，合同ID: {}", contractId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 执行全面分析
     */
    @PostMapping("/full-analysis/{contractId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRACT_OPERATOR')")
    public ResponseEntity<List<ContractAnalysisResultDto>> performFullAnalysis(@PathVariable Long contractId) {
        logger.info("执行全面合同分析，合同ID: {}", contractId);
        
        try {
            List<ContractAnalysisResultDto> results = analysisService.performFullAnalysis(contractId);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("全面分析执行失败，合同ID: {}", contractId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 版本比对
     */
    @GetMapping("/compare/{contractId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRACT_OPERATOR')")
    public String compareVersionsPage(@PathVariable Long contractId, Model model) {
        model.addAttribute("contractId", contractId);
        return "analysis/version-comparison";
    }

    /**
     * 执行版本比对
     */
    @PostMapping("/compare/{contractId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRACT_OPERATOR')")
    public ResponseEntity<ContractComparisonDto> compareVersions(
            @PathVariable Long contractId,
            @RequestParam Integer fromVersion,
            @RequestParam Integer toVersion) {
        
        logger.info("执行版本比对，合同ID: {}, 从版本: {}, 到版本: {}", contractId, fromVersion, toVersion);
        
        try {
            ContractComparisonDto result = analysisService.compareVersions(contractId, fromVersion, toVersion);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("版本比对执行失败，合同ID: {}", contractId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 获取分析历史
     */
    @GetMapping("/history/{contractId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRACT_OPERATOR')")
    public ResponseEntity<List<ContractAnalysisResultDto>> getAnalysisHistory(
            @PathVariable Long contractId,
            @RequestParam(required = false) AnalysisType analysisType) {
        
        try {
            List<ContractAnalysisResultDto> history = analysisService.getAnalysisHistory(contractId, analysisType);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            logger.error("获取分析历史失败，合同ID: {}", contractId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 高风险合同列表页面
     */
    @GetMapping("/high-risk")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRACT_OPERATOR')")
    public String highRiskContractsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Model model) {
        
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<ContractAnalysisResultDto> highRiskContracts = analysisService.getHighRiskContracts(pageable);
        
        model.addAttribute("contracts", highRiskContracts);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", highRiskContracts.getTotalPages());
        model.addAttribute("totalElements", highRiskContracts.getTotalElements());
        
        return "analysis/high-risk-contracts";
    }

    /**
     * 批量分析
     */
    @PostMapping("/batch-analyze")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ContractAnalysisResultDto>> batchAnalyze(
            @RequestParam List<Long> contractIds,
            @RequestParam AnalysisType analysisType) {
        
        logger.info("批量分析合同，数量: {}, 分析类型: {}", contractIds.size(), analysisType);
        
        try {
            List<ContractAnalysisResultDto> results = analysisService.batchAnalyze(contractIds, analysisType);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("批量分析执行失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 重新分析过期的分析结果
     */
    @PostMapping("/reanalyze-outdated")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Integer> reanalyzeOutdatedContracts(@RequestParam(defaultValue = "30") int daysOld) {
        logger.info("重新分析超过{}天的分析结果", daysOld);
        
        try {
            int count = analysisService.reanalyzeOutdatedContracts(daysOld);
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            logger.error("重新分析执行失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 获取可分析的合同列表
     */
    @GetMapping("/available-contracts")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRACT_OPERATOR')")
    @ResponseBody
    public List<Map<String, Object>> getAvailableContracts() {
        logger.info("获取可分析的合同列表");
        try {
            return analysisService.getAvailableContracts();
        } catch (Exception e) {
            logger.error("获取可分析合同列表失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取最近的分析记录
     */
    @GetMapping("/recent-analyses")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTRACT_OPERATOR')")
    @ResponseBody
    public List<ContractAnalysisResultDto> getRecentAnalyses(
            @RequestParam(defaultValue = "10") int limit) {
        logger.info("获取最近的分析记录，限制数量: {}", limit);
        try {
            return analysisService.getRecentAnalyses(limit);
        } catch (Exception e) {
            logger.error("获取最近分析记录失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 风险统计API
     */
    @GetMapping("/risk-statistics")
    @ResponseBody
    public Map<String, Long> getRiskStatistics() {
        Map<RiskLevel, Long> enumStats = analysisService.getRiskLevelStatistics();
        Map<String, Long> stringStats = new HashMap<>();
        
        // 将枚举键转换为字符串键
        for (Map.Entry<RiskLevel, Long> entry : enumStats.entrySet()) {
            stringStats.put(entry.getKey().name(), entry.getValue());
        }
        
        return stringStats;
    }
} 