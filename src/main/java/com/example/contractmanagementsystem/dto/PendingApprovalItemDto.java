package com.example.contractmanagementsystem.dto;

import com.example.contractmanagementsystem.entity.ContractProcess;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 用于在“待审批合同”页面显示合同流程和其附件的DTO。
 * 这个DTO封装了ContractProcess实体和从合同中解析出来的附件文件名列表。
 */
public class PendingApprovalItemDto {
    private ContractProcess contractProcess;
    private List<String> attachmentFileNames;
    @Setter
    private String attachmentFileNamesAsJson; // <--- 新增字段

    /**
     * 构造函数。
     * @param contractProcess 待审批的合同流程实体。
     * @param attachmentFileNames 从合同附件路径JSON字符串中解析出来的文件名列表。
     */
    public PendingApprovalItemDto(ContractProcess contractProcess, List<String> attachmentFileNames) {
        this.contractProcess = contractProcess;
        // 确保 attachmentFileNames 不为 null，避免NPE
        this.attachmentFileNames = attachmentFileNames != null ? new ArrayList<>(attachmentFileNames) : new ArrayList<>();
    }

    // --- 不需要修改已有的 Getter ---
    public ContractProcess getContractProcess() {
        return contractProcess;
    }

    public List<String> getAttachmentFileNames() {
        return attachmentFileNames;
    }

    // --- 新增 Getter 和 Setter ---
    public String getAttachmentFileNamesAsJson() {
        return attachmentFileNamesAsJson;
    }

}