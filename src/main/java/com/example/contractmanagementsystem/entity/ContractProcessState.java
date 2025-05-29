package com.example.contractmanagementsystem.entity;

public enum ContractProcessState {
    PENDING("待处理"),
    APPROVED("已通过"),
    REJECTED("已拒绝"),
    COMPLETED("已完成");

    private final String description;

    ContractProcessState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}