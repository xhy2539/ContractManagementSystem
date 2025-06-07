package com.example.contractmanagementsystem.dto;

import com.example.contractmanagementsystem.entity.ContractProcess;
import java.util.ArrayList;
import java.util.List;

/**
 * 用于在“待审批合同”页面显示合同流程和其附件的DTO。
 * 这个DTO封装了ContractProcess实体和从合同中解析出来的附件文件名列表。
 */
public class PendingApprovalItemDto {
    private ContractProcess contractProcess;
    private List<String> attachmentFileNames;

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

    /**
     * 获取合同流程实体。
     * @return ContractProcess 实体。
     */
    public ContractProcess getContractProcess() {
        return contractProcess;
    }

    /**
     * 获取附件文件名列表。
     * @return 附件文件名字符串列表。
     */
    public List<String> getAttachmentFileNames() {
        return attachmentFileNames;
    }

    // 如果需要，可以添加setter方法，但对于此用例，构造函数设置即可
}
