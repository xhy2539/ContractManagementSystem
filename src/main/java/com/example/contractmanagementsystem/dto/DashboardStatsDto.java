package com.example.contractmanagementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto {
    private long activeContractsCount;
    private long expiringSoonCount;
    private long expiredContractsCount;
    private long inProcessContractsCount;
    private long pendingAssignmentCount;
}