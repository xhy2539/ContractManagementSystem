package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.service.ContractService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable; // 确保导入 Pageable
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication; // 新增导入
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/contract-info-manager")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_CONTRACT_OPERATOR')")
public class ContractInfoManagerController {

    @Autowired
    private ContractService contractService;

    /**
     * 显示合同信息管理页面
     */
    @GetMapping
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasAuthority('MANAGE_CONTRACT_INFO')")
    public String showContractInfoManagerPage(Model model) {
        return "baseData/contract-info-manager";
    }

    /**
     * 搜索合同信息（分页）
     */
    @GetMapping("/api/contracts/search")
    @ResponseBody
    // 权限可以保持不变
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasAuthority('QUERY_CONTRACT_INFO')")
    public Page<Contract> searchContracts(
            Authentication authentication, // 获取认证信息
            @RequestParam(required = false) String contractName,
            @RequestParam(required = false) String contractNumber,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String currentUsername = null;
        boolean isAdmin = false;

        if (authentication != null && authentication.isAuthenticated()) {
            currentUsername = authentication.getName();
            // 确保 isPresent() 调用在任何 Stream 操作之前
            isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(ga -> ga.getAuthority().equals("ROLE_ADMIN"));
        }

        String usernameForSearchFilter = isAdmin ? null : currentUsername;

        Pageable pageable = PageRequest.of(page, size);
        return contractService.searchContracts(usernameForSearchFilter, isAdmin, contractName, contractNumber, status, pageable);
    }

    @DeleteMapping("/delete/{contractId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> deleteContract(@PathVariable Long contractId) {
        try {
            contractService.deleteContract(contractId);
            return ResponseEntity.ok().body(Map.of(
                    "message", "合同删除成功"
            ));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "合同不存在",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "删除失败",
                    "message", e.getMessage()
            ));
        }
    }
}