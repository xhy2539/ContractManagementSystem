package com.example.contractmanagementsystem.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class ContractExtendRequestOperator {
    @NotNull(message = "期望新的到期日期不能为空")
    @Future(message = "期望新的到期日期必须是将来日期") // 确保延期日期在今天之后
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate requestedNewEndDate;

    @NotBlank(message = "延期原因不能为空")
    private String reason; // 延期原因（操作员请求延期）

    private String comments; // 附加备注（操作员请求延期）
}