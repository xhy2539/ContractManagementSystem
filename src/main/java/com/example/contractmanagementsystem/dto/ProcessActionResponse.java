package com.example.contractmanagementsystem.dto;

public class ProcessActionResponse {
    private String message;
    private boolean processComplete;
    private String contractStatus;

    public ProcessActionResponse(String message, boolean processComplete, String contractStatus) {
        this.message = message;
        this.processComplete = processComplete;
        this.contractStatus = contractStatus;
    }

    // --- 添加标准 Getters 和 Setters ---
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public boolean isProcessComplete() { return processComplete; }
    public void setProcessComplete(boolean processComplete) { this.processComplete = processComplete; }
    public String getContractStatus() { return contractStatus; }
    public void setContractStatus(String contractStatus) { this.contractStatus = contractStatus; }
}