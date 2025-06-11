package com.example.contractmanagementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ContractExtensionApprovalRequest {
    @NotBlank(message = "审批决定不能为空")
    private String decision; // APPROVED or REJECTED
    private String comments; // Approval comments
}