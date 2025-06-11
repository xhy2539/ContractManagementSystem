package com.example.contractmanagementsystem.dto;

import com.example.contractmanagementsystem.entity.ContractProcess;
import java.util.ArrayList;
import java.util.List;

public class PendingApprovalItemDto {
    private ContractProcess contractProcess;
    private List<String> attachmentFileNames;
    private String attachmentFileNamesAsJson; // <--- 必须有这个字段

    public PendingApprovalItemDto(ContractProcess contractProcess, List<String> attachmentFileNames) {
        this.contractProcess = contractProcess;
        this.attachmentFileNames = attachmentFileNames != null ? new ArrayList<>(attachmentFileNames) : new ArrayList<>();
    }

    // Getters
    public ContractProcess getContractProcess() { return contractProcess; }
    public List<String> getAttachmentFileNames() { return attachmentFileNames; }
    public String getAttachmentFileNamesAsJson() { return attachmentFileNamesAsJson; }

    // Setters
    public void setContractProcess(ContractProcess contractProcess) { this.contractProcess = contractProcess; }
    public void setAttachmentFileNames(List<String> attachmentFileNames) { this.attachmentFileNames = attachmentFileNames; }
    public void setAttachmentFileNamesAsJson(String attachmentFileNamesAsJson) { this.attachmentFileNamesAsJson = attachmentFileNamesAsJson; }
}