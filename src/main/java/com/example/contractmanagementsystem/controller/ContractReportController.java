package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.service.ContractService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable; // 确保导入 Pageable
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication; // 新增导入
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/reports")
// 权限保持不变：允许ADMIN或CONTRACT_OPERATOR访问此控制器
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_CONTRACT_OPERATOR')")
public class ContractReportController {

    @Autowired
    private ContractService contractService;

    @GetMapping("/contract-status")
    // 权限可以保持不变，或者根据具体需求调整为更细致的功能权限
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasAuthority('REPORT_STATUS_VIEW')")
    public String showContractStatusReport(Model model) {
        return "reports/contract-status";
    }

    @GetMapping("/contract-search")
    // 权限可以保持不变，或者根据具体需求调整为更细致的功能权限
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasAuthority('QUERY_CONTRACT_INFO')")
    public String showContractSearchPage(Model model) {
        return "reports/contract-search";
    }

    @GetMapping("/api/contract-status-data")
    @ResponseBody
    // 权限可以保持不变
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasAuthority('REPORT_STATUS_VIEW')")
    public Map<String, Long> getContractStatusData() {
        return contractService.getContractStatusStatistics();
    }

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

        // 核心修改：
        // 如果用户不是管理员 (即此时可能是 CONTRACT_OPERATOR 或其他非管理员角色)，
        // 我们将传递 currentUsername 给 searchContracts 方法。
        // ContractServiceImpl.searchContracts 方法在 isAdmin 为 false 且 currentUsername 非空时，
        // 会应用基于 drafter 或参与流程的过滤。
        // 如果“与自己相关的合同”对于操作员意味着更广泛的范围（例如，参与流程的合同），
        // 那么 ContractServiceImpl.searchContracts 中的用户过滤逻辑需要相应扩展。
        // 当前的修改将使得非管理员用户（包括操作员）通过此API查询时，结果会按起草人或参与流程过滤。
        String usernameForSearchFilter = isAdmin ? null : currentUsername;

        Pageable pageable = PageRequest.of(page, size);
        return contractService.searchContracts(usernameForSearchFilter, isAdmin, contractName, contractNumber, status, pageable);
    }
}