package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.service.ContractQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contracts/query")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class ContractQueryController {

    private final ContractQueryService contractQueryService;

    @Autowired
    public ContractQueryController(ContractQueryService contractQueryService) {
        this.contractQueryService = contractQueryService;
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('CON_QUERY_VIEW')")
    public ResponseEntity<Page<Contract>> searchContracts(
            @RequestParam(required = false) String contractName,
            @RequestParam(required = false) String contractNumber,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(contractQueryService.searchContracts(contractName, contractNumber, pageable));
    }

    @GetMapping("/by-status")
    @PreAuthorize("hasAuthority('CON_QUERY_VIEW')")
    public ResponseEntity<Page<Contract>> getContractsByStatus(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(contractQueryService.getContractsByStatus(status, pageable));
    }
} 