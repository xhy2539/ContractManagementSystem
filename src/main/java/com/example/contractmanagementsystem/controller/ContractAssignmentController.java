package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.ContractAssignmentRequest;
import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.service.SystemManagementService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/contract-assignments")
@PreAuthorize("hasRole('ROLE_ADMIN')") // 只有管理员角色可以访问此控制器的所有端点
public class ContractAssignmentController {

    private final SystemManagementService systemManagementService;

    @Autowired
    public ContractAssignmentController(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    /**
     * 获取待分配处理人员的合同列表。
     * 需要 'CON_ASSIGN_VIEW' 权限。
     * @param pageable 分页和排序参数。
     * @param contractNameSearch 按合同名称搜索（可选）。
     * @param contractNumberSearch 按合同编号搜索（可选）。
     * @return 分页的合同数据。
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('CON_ASSIGN_VIEW')") // 根据功能编号进行权限控制
    public ResponseEntity<Page<Contract>> getContractsPendingAssignment(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String contractNameSearch,
            @RequestParam(required = false) String contractNumberSearch) {
        // 调用服务层方法，该方法现在会查询状态为 PENDING_ASSIGNMENT 的合同
        Page<Contract> contractsPage = systemManagementService.getContractsPendingAssignment(pageable, contractNameSearch, contractNumberSearch);
        return ResponseEntity.ok(contractsPage);
    }

    /**
     * 为指定合同分配处理人员（会签、审批、签订，定稿）。
     * 需要 'CON_ASSIGN_DO' 权限。
     * @param contractId 要分配人员的合同ID。
     * @param request 包含各类处理人员ID列表的请求体。
     * @return HTTP 200 OK 响应如果分配成功。
     */
    @PostMapping("/{contractId}/assign")
    @PreAuthorize("hasAuthority('CON_ASSIGN_DO')") // 根据功能编号进行权限控制
    public ResponseEntity<Void> assignPersonnelToContract(
            @PathVariable Long contractId,
            @Valid @RequestBody ContractAssignmentRequest request) {
        // 调用服务层方法，该方法现在会一次性分配所有角色，
        // 并将合同状态从 PENDING_ASSIGNMENT 更新为 PENDING_COUNTERSIGN
        systemManagementService.assignContractPersonnel(
                contractId,
                request.getCountersignUserIds(),
                request.getApprovalUserIds(),
                request.getSignUserIds(),
                request.getFinalizeUserIds() // 新增传递定稿人员ID
        );
        return ResponseEntity.ok().build(); // 返回成功响应
    }
}