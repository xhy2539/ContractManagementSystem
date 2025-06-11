package com.example.contractmanagementsystem.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class ContractExtendRequest {
    @NotNull(message = "新的到期日期不能为空")
    @Future(message = "新的到期日期必须是将来日期") // 确保延期日期在今天之后
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate newEndDate;

    private String comments; // 延期备注（管理员直接延期）
}