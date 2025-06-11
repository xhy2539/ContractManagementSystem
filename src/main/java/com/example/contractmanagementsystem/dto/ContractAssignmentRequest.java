package com.example.contractmanagementsystem.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class ContractAssignmentRequest {
    @NotEmpty(message = "必须至少指定一名定稿人员。")
    private List<Long> finalizerUserIds;    // 定稿用户ID列表

    @NotEmpty(message = "必须至少指定一名会签人员。")
    private List<Long> countersignUserIds; // 会签用户ID列表

    @NotEmpty(message = "必须至少指定一名审批人员。")
    private List<Long> approvalUserIds;  // 审批用户ID列表

    @NotEmpty(message = "必须至少指定一名签订人员。")
    private List<Long> signUserIds;      // 签订用户ID列表

    // Getters
    public List<Long> getFinalizerUserIds() {
        return finalizerUserIds;
    }

    public List<Long> getCountersignUserIds() {
        return countersignUserIds;
    }

    public List<Long> getApprovalUserIds() {
        return approvalUserIds;
    }

    public List<Long> getSignUserIds() {
        return signUserIds;
    }

    // Setters
    public void setFinalizerUserIds(List<Long> finalizerUserIds) {
        this.finalizerUserIds = finalizerUserIds;
    }

    public void setCountersignUserIds(List<Long> countersignUserIds) {
        this.countersignUserIds = countersignUserIds;
    }

    public void setApprovalUserIds(List<Long> approvalUserIds) {
        this.approvalUserIds = approvalUserIds;
    }

    public void setSignUserIds(List<Long> signUserIds) {
        this.signUserIds = signUserIds;
    }
}