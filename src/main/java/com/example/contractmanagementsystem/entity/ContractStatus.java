package com.example.contractmanagementsystem.entity; // 或 com.example.contractmanagementsystem.enums;

public enum ContractStatus {
    DRAFT("起草"),
    PENDING_ASSIGNMENT("待分配"), // 如果分配是一个独立状态
    PENDING_COUNTERSIGN("待会签"),
    PENDING_APPROVAL("待审批"),
    PENDING_SIGNING("待签订"),
    ACTIVE("有效"),
    COMPLETED("完成"),
    EXPIRED("过期"),
    TERMINATED("终止"),
    REJECTED("已拒绝");

    private final String description;

    ContractStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return this.name(); // 返回枚举名作为数据库存储值，或自定义一个code字段
    }
}