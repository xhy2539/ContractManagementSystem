package com.example.contractmanagementsystem.entity;

public enum ContractStatus {
    DRAFT("起草"),
    PENDING_ASSIGNMENT("待分配"), // 如果分配是一个独立状态
    PENDING_COUNTERSIGN("待会签"),
    PENDING_FINALIZATION("待定稿"), // 新增：合同进入等待最终定稿的状态
    PENDING_APPROVAL("待审批"),
    PENDING_SIGNING("待签订"),
    ACTIVE("有效"),
    COMPLETED("完成"),
    EXPIRED("过期"),
    TERMINATED("终止"),
    REJECTED("已拒绝"); // 合同在审批等环节被拒绝

    private final String description;

    ContractStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 返回枚举的名称，通常用于数据库存储或内部逻辑判断。
     * @return 枚举的名称 (例如, "DRAFT", "PENDING_FINALIZATION")
     */
    @Override
    public String toString() {
        return this.name();
    }
}