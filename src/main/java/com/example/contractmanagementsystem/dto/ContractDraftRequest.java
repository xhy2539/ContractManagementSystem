package com.example.contractmanagementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Map; // 新增导入 Map

public class ContractDraftRequest {

    // Getters and Setters
    @Setter
    @Getter
    @NotBlank(message = "合同名称不能为空")
    private String contractName;

    @Setter
    @Getter
    @NotNull(message = "必须选择一个客户")
    private Long selectedCustomerId;

    @Setter
    @Getter
    @NotNull(message = "开始时间不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @Setter
    @Getter
    @NotNull(message = "结束时间不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @Setter
    @Getter
    private String contractContent; // 用于起草时的初始合同内容

    // 新增 updatedContent 的 Getter 和 Setter
    // 新增字段：用于定稿时传递更新后的合同内容
    @Getter
    @Setter
    private String updatedContent;

    // 用于存储附件文件名列表的JSON字符串 (在起草和定稿时都可能用到)
    @Setter
    @Getter
    private List<String> attachmentServerFileNames = new ArrayList<>();

    // 新增 templateId 的 Getter 和 Setter
    // 新增字段：用于选择模板的ID (可选)
    @Setter
    @Getter
    private Long templateId;

    // 新增 placeholderValues 的 Getter 和 Setter
    // 新增字段：用于传递占位符的具体值，键为占位符名称，值为其对应的内容
    @Setter
    @Getter
    private Map<String, String> placeholderValues;

    @Setter
    @Getter
    private String signatureDataPartyA; // 甲方签名数据
    @Setter
    @Getter
    private String signatureDataPartyB; // 乙方签名数据
    @Setter
    @Getter
    private String signatureData;



}