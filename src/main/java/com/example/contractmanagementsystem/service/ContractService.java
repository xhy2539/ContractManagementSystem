package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.Contract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface ContractService {
    /**
     * 获取各个状态的合同数量统计
     * @return 状态-数量映射
     */
    Map<String, Long> getContractStatusStatistics();

    /**
     * 搜索合同
     * @param contractName 合同名称（可选）
     * @param contractNumber 合同编号（可选）
     * @param status 合同状态（可选）
     * @param pageable 分页参数
     * @return 合同分页数据
     */
    Page<Contract> searchContracts(String contractName, String contractNumber, String status, Pageable pageable);
} 