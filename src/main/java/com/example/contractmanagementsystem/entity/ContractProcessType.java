package com.example.contractmanagementsystem.entity;

import lombok.Getter;

@Getter
public enum ContractProcessType {
    COUNTERSIGN(0, "会签"),
    FINALIZE(1, "定稿"),
    APPROVAL(2, "审批"),
    SIGNING(3, "签订"),
    EXTENSION(4, "延期"); // 新增

    private final int code;
    private final String description;

    ContractProcessType(int code, String description) {
        this.code = code;
        this.description = description;
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