package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.service.ContractService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/reports")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_CONTRACT_OPERATOR')")
public class ContractReportController {

    @Autowired
    private ContractService contractService;

    @GetMapping("/contract-status")
    public String showContractStatusReport(Model model) {
        return "reports/contract-status";
    }

    @GetMapping("/contract-search")
    public String showContractSearchPage(Model model) {
        return "reports/contract-search";
    }

    @GetMapping("/api/contract-status-data")
    @ResponseBody
    public Map<String, Long> getContractStatusData() {
        return contractService.getContractStatusStatistics();
    }

    @GetMapping("/api/contracts/search")
    @ResponseBody
    public Page<Contract> searchContracts(
            @RequestParam(required = false) String contractName,
            @RequestParam(required = false) String contractNumber,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return contractService.searchContracts(contractName, contractNumber, status, PageRequest.of(page, size));
    }
} 