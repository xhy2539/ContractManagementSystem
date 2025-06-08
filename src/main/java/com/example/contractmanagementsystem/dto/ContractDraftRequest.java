package com.example.contractmanagementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Map; // 新增导入 Map

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

    private String contractContent; // 用于起草时的初始合同内容

    // 新增字段：用于定稿时传递更新后的合同内容
    private String updatedContent;

    // 用于存储附件文件名列表的JSON字符串 (在起草和定稿时都可能用到)
    private List<String> attachmentServerFileNames = new ArrayList<>();

    // 新增字段：用于选择模板的ID (可选)
    private Long templateId;

    // 新增字段：用于传递占位符的具体值，键为占位符名称，值为其对应的内容
    private Map<String, String> placeholderValues;


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

    public List<String> getAttachmentServerFileNames() {
        return attachmentServerFileNames;
    }

    public void setAttachmentServerFileNames(List<String> attachmentServerFileNames) {
        this.attachmentServerFileNames = attachmentServerFileNames;
    }

    // 新增 updatedContent 的 Getter 和 Setter
    public String getUpdatedContent() {
        return updatedContent;
    }

    public void setUpdatedContent(String updatedContent) {
        this.updatedContent = updatedContent;
    }

    // 新增 templateId 的 Getter 和 Setter
    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    // 新增 placeholderValues 的 Getter 和 Setter
    public Map<String, String> getPlaceholderValues() {
        return placeholderValues;
    }

    public void setPlaceholderValues(Map<String, String> placeholderValues) {
        this.placeholderValues = placeholderValues;
    }
}