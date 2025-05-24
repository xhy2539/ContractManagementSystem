package com.example.contractmanagementsystem.entity;

public enum ContractProcessType {
    COUNTERSIGN(1, "会签"),
    APPROVAL(2, "审批"),
    SIGNING(3, "签订");

    private final int code;
    private final String description;

    ContractProcessType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static ContractProcessType fromCode(int code) {
        for (ContractProcessType type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的操作类型代码: " + code);
    }
}