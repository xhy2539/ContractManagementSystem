package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.ContractAssignmentRequest;
import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.service.SystemManagementService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page; // 新增导入
import org.springframework.data.domain.Pageable; // 新增导入
import org.springframework.data.domain.Sort; // 新增导入
import org.springframework.data.web.PageableDefault; // 新增导入
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// List的导入不再需要，除非其他地方用到
// import java.util.List;

@RestController
@RequestMapping("/api/admin/contract-assignments")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class ContractAssignmentController {

    private final SystemManagementService systemManagementService;

    @Autowired
    public ContractAssignmentController(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    /**
     * 获取待分配的合同列表（支持分页和搜索）。
     * 待分配的合同是指状态为“起草”或“待分配”，并且尚未有任何流程记录的合同。
     * @param pageable 分页和排序参数 (例如 ?page=0&size=10&sort=createdAt,desc)
     * @param contractNameSearch 可选的合同名称搜索关键词
     * @param contractNumberSearch 可选的合同编号搜索关键词
     * @return 合同的分页数据
     */
    @GetMapping("/pending")
    public ResponseEntity<Page<Contract>> getContractsPendingAssignment(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String contractNameSearch,
            @RequestParam(required = false) String contractNumberSearch) {
        // 调用已更新的支持分页和搜索的Service方法
        Page<Contract> contractsPage = systemManagementService.getContractsPendingAssignment(pageable, contractNameSearch, contractNumberSearch);
        return ResponseEntity.ok(contractsPage);
    }

    /**
     * 为指定的合同分配处理人员。
     * @param contractId 要分配人员的合同ID
     * @param request 包含会签、审批、签订人员ID列表的请求体
     * @return HTTP 200 OK 如果成功
     */
    @PostMapping("/{contractId}/assign")
    public ResponseEntity<Void> assignPersonnelToContract(
            @PathVariable Long contractId,
            @Valid @RequestBody ContractAssignmentRequest request) {
        systemManagementService.assignContractPersonnel(
                contractId,
                request.getCountersignUserIds(),
                request.getApprovalUserIds(),
                request.getSignUserIds()
        );
        // 考虑 assignContractPersonnel 返回 boolean 值，可以根据结果返回不同状态码
        // 例如，如果分配失败（没有人员被实际分配），可以返回 HttpStatus.BAD_REQUEST 或其他
        return ResponseEntity.ok().build();
    }
}