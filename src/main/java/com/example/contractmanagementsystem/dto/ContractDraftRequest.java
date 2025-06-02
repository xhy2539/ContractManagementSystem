// File: src/main/java/com/example/contractmanagementsystem/dto/ContractDraftRequest.java
package com.example.contractmanagementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List; // 新增导入
import java.util.ArrayList; // 新增导入

public class ContractDraftRequest {

    @NotBlank(message = "合同名称不能为空")
    private String contractName;

    @NotNull(message = "必须选择一个客户")
    private Long selectedCustomerId;

    @NotNull(message = "开始时间不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @NotNull(message = "结束时间不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private String contractContent;

    // 修改点：从 String 变为 List<String>
    private List<String> attachmentServerFileNames = new ArrayList<>();

    // Getters and Setters
    public String getContractName() {
        return contractName;
    }

    public void setContractName(String contractName) {
        this.contractName = contractName;
    }

    public Long getSelectedCustomerId() {
        return selectedCustomerId;
    }

    public void setSelectedCustomerId(Long selectedCustomerId) {
        this.selectedCustomerId = selectedCustomerId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getContractContent() {
        return contractContent;
    }

    public void setContractContent(String contractContent) {
        this.contractContent = contractContent;
    }

    // 修改点：更新 Getter 和 Setter 以处理 List<String>
    public List<String> getAttachmentServerFileNames() {
        return attachmentServerFileNames;
    }

    public void setAttachmentServerFileNames(List<String> attachmentServerFileNames) {
        this.attachmentServerFileNames = attachmentServerFileNames;
    }
}