package com.example.contractmanagementsystem.service;

public interface NotificationService {
    void notifyContractStatusChange(Long contractId, String status);
} 