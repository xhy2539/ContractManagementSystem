package com.example.contractmanagementsystem.entity;

public enum ContractProcessState {
    PENDING(0, "未完成"),
    COMPLETED(1, "已完成"),
    REJECTED(2, "已否决");

    private final int code;
    private final String description;

    ContractProcessState(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static ContractProcessState fromCode(int code) {
        for (ContractProcessState state : values()) {
            if (state.getCode() == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("无效的操作状态代码: " + code);
    }
}