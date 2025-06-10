// src/main/java/com/example/contractmanagementsystem/entity/ContractStatus.java
package com.example.contractmanagementsystem.entity;

public enum ContractStatus {
    DRAFT("起草"), // 用户起草后的初始状态，在明确提交分配前
    PENDING_ASSIGNMENT("待分配"), // 新增：合同已提交，等待管理员分配处理人员
    PENDING_COUNTERSIGN("待会签"), // 人员已分配，等待会签
    PENDING_FINALIZATION("待定稿"), // 会签完成，等待起草人/负责人定稿
    PENDING_APPROVAL("待审批"),   // 已定稿，等待审批
    PENDING_SIGNING("待签订"),    // 已审批，等待签订
    ACTIVE("有效"),
    COMPLETED("完成"),
    EXPIRED("过期"),
    REJECTED("已拒绝"); // 在任何流程中被拒绝

    private final String description;

    ContractStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return this.name(); // 用于数据库存储或内部逻辑判断
    }
}