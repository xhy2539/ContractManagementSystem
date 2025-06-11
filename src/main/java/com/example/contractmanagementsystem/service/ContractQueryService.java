package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.Contract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ContractQueryService {
    
    /**
     * 根据合同名称和编号搜索合同
     * @param contractName 合同名称（可选）
     * @param contractNumber 合同编号（可选）
     * @param pageable 分页参数
     * @return 合同分页数据
     */
    Page<Contract> searchContracts(String contractName, String contractNumber, Pageable pageable);

    /**
     * 根据合同状态查询合同
     * @param status 合同状态
     * @param pageable 分页参数
     * @return 合同分页数据
     */
    Page<Contract> getContractsByStatus(String status, Pageable pageable);
} 