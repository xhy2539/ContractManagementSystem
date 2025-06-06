package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.service.ContractService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper; // 新增导入
import com.fasterxml.jackson.core.JsonProcessingException; // 新增导入

@Controller
@RequestMapping("/reports")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_CONTRACT_OPERATOR')")
public class ContractReportController {

    @Autowired
    private ContractService contractService;
    private final ObjectMapper objectMapper; // 引入 ObjectMapper

    @Autowired // 确保注入 ObjectMapper
    public ContractReportController(ContractService contractService, ObjectMapper objectMapper) {
        this.contractService = contractService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/contract-status")
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasAuthority('REPORT_STATUS_VIEW')")
    public String showContractStatusReport(Model model) {
        return "reports/contract-status";
    }

    @GetMapping("/contract-search")
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasAuthority('QUERY_CONTRACT_INFO')")
    public String showContractSearchPage(Model model, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            List<String> authoritiesList = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());
            try {
                String authoritiesJson = objectMapper.writeValueAsString(authoritiesList);
                model.addAttribute("currentUserAuthoritiesJson", authoritiesJson); // 传递 JSON 字符串
            } catch (JsonProcessingException e) {
                System.err.println("Error serializing authorities to JSON: " + e.getMessage());
                model.addAttribute("currentUserAuthoritiesJson", "[]"); // 序列化失败时传递空 JSON 数组
            }
        } else {
            model.addAttribute("currentUserAuthoritiesJson", "[]"); // 未认证用户传递空 JSON 数组
        }
        return "reports/contract-search";
    }

    @GetMapping("/api/contract-status-data")
    @ResponseBody
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasAuthority('REPORT_STATUS_VIEW')")
    public Map<String, Long> getContractStatusData() {
        return contractService.getContractStatusStatistics();
    }

    @GetMapping("/api/contracts/search")
    @ResponseBody
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasAuthority('QUERY_CONTRACT_INFO')")
    public Page<Contract> searchContracts(
            Authentication authentication,
            @RequestParam(required = false) String contractName,
            @RequestParam(required = false) String contractNumber,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String currentUsername = null;
        boolean isAdmin = false;

        if (authentication != null && authentication.isAuthenticated()) {
            currentUsername = authentication.getName();
            isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(ga -> ga.getAuthority().equals("ROLE_ADMIN"));
        }

        String usernameForSearchFilter = isAdmin ? null : currentUsername;

        Pageable pageable = PageRequest.of(page, size);
        return contractService.searchContracts(usernameForSearchFilter, isAdmin, contractName, contractNumber, status, pageable);
    }
}