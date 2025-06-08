

package com.example.contractmanagementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class ContractDraftRequest {

    // 合同基本信息
    private Long templateId; // 合同模板ID，可选
    @NotBlank(message = "合同名称不能为空")
    @Size(max = 255, message = "合同名称不能超过255个字符")
    private String contractName;

    @NotNull(message = "开始日期不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    // 客户信息
    @NotNull(message = "请选择一个签约客户")
    private Long selectedCustomerId;

    // 合同内容 (直接是 TinyMCE 输出的HTML，包含<span>标签)
    @NotBlank(message = "合同主要内容不能为空")
    private String contractContent;

    // 占位符具体值 (从前端解析的Map<String, String>，键是占位符名称，值是用户填写的)
    // Spring会自动将前端传来的JSON字符串绑定到Map
    private Map<String, String> placeholderValues;

    // 附件文件名列表 (服务器上已保存的临时文件，需要移动到正式目录)
    private List<String> attachmentServerFileNames;

    // --- 新增：电子签名数据 ---
    // Base64 编码的签名图片数据
    private String signatureData; // 可以是 data:image/png;base64,... 格式的字符串
    // --- 新增结束 ---

}