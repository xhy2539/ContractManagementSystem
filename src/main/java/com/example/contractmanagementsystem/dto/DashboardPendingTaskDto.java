package com.example.contractmanagementsystem.dto;

import com.example.contractmanagementsystem.entity.ContractProcessType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class DashboardPendingTaskDto {

    private Long processId;
    private ContractProcessType type;
    private String typeDescription;
    private LocalDateTime createdAt;

    private Long contractId;
    private String contractName;
    private String contractNumber;
    private String drafterUsername;

    // 构造函数，用于JPA查询
    public DashboardPendingTaskDto(Long processId, ContractProcessType type, LocalDateTime createdAt,
                                   Long contractId, String contractName, String contractNumber, String drafterUsername) {
        this.processId = processId;
        this.type = type;
        this.typeDescription = type.getDescription(); // 直接设置描述
        this.createdAt = createdAt;
        this.contractId = contractId;
        this.contractName = contractName;
        this.contractNumber = contractNumber;
        this.drafterUsername = drafterUsername;
    }
}