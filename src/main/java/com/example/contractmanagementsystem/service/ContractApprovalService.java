package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;

import java.util.Optional;

public interface ContractApprovalService { // 接口名修改为 ContractApprovalService

    /**
     * 根据合同ID获取合同。
     * @param contractId 合同ID
     * @return 包含合同的 Optional 对象
     */
    Optional<Contract> getContractById(Long contractId);

    /**
     * 处理合同审批操作（通过或拒绝），记录到 ContractProcess，并更新合同状态。
     * 同时也负责权限检查和业务逻辑验证。
     *
     * @param contractId 待审批合同的ID
     * @param username 当前审批人的用户名 (用于查找用户和权限验证)
     * @param isApproved 审批决定 (true 为批准，false 为拒绝)
     * @param comments 审批意见
     * @throws ResourceNotFoundException 如果合同或审批人未找到
     * @throws BusinessLogicException 如果合同状态不允许审批或业务逻辑不符
     * @throws org.springframework.security.access.AccessDeniedException 如果用户没有权限审批该合同
     */
    void processApproval(Long contractId, String username, boolean isApproved, String comments)
            throws ResourceNotFoundException, BusinessLogicException, org.springframework.security.access.AccessDeniedException;

    /**
     * 检查当前用户是否有权审批指定合同。
     * @param username 当前用户名称
     * @param contractId 合同ID
     * @return 如果有权审批返回 true，否则返回 false
     */
    boolean canUserApproveContract(String username, Long contractId);
}