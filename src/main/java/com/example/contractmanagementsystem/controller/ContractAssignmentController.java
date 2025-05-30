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
@RequestMapping("/admin/contract-assignments")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class ContractAssignmentController {

    private final SystemManagementService systemManagementService;

    @Autowired
    public ContractAssignmentController(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('CON_ASSIGN_VIEW')") // 使用功能编号
    public ResponseEntity<Page<Contract>> getContractsPendingAssignment(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String contractNameSearch,
            @RequestParam(required = false) String contractNumberSearch) {
        Page<Contract> contractsPage = systemManagementService.getContractsPendingAssignment(pageable, contractNameSearch, contractNumberSearch);
        return ResponseEntity.ok(contractsPage);
    }

    @PostMapping("/{contractId}/assign")
    @PreAuthorize("hasAuthority('CON_ASSIGN_DO')") // 使用功能编号
    public ResponseEntity<Void> assignPersonnelToContract(
            @PathVariable Long contractId,
            @Valid @RequestBody ContractAssignmentRequest request) {
        systemManagementService.assignContractPersonnel(
                contractId,
                request.getCountersignUserIds(),
                request.getApprovalUserIds(),
                request.getSignUserIds()
        );
        return ResponseEntity.ok().build();
    }
}