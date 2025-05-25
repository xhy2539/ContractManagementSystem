package com.example.contractmanagementsystem.dto;

import java.util.List;

public class ContractAssignmentRequest {
    private List<Long> countersignUserIds; // 会签用户ID列表
    private List<Long> approvalUserIds;  // 审批用户ID列表
    private List<Long> signUserIds;      // 签订用户ID列表

    // Getters
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