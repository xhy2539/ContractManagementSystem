package com.example.contractmanagementsystem.service;

public interface NotificationService {
    // 合同状态变更通知
    void notifyContractStatusChange(Long contractId, String status);
    
    // 新合同分配通知
    void notifyContractAssignment(Long contractId, String assignedTo);
    
    // 新业务创建通知
    void notifyNewBusiness(Long businessId, String businessType);
    
    // 任务列表更新通知
    void notifyTaskListUpdate(String username);
} 