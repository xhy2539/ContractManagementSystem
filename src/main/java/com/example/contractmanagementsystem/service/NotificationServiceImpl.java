package com.example.contractmanagementsystem.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public NotificationServiceImpl(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void notifyContractStatusChange(Long contractId, String status) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "CONTRACT_STATUS_CHANGE");
        notification.put("contractId", contractId);
        notification.put("status", status);
        notification.put("timestamp", System.currentTimeMillis());
        
        messagingTemplate.convertAndSend("/topic/contract-updates", notification);
    }

    @Override
    public void notifyContractAssignment(Long contractId, String assignedTo) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "CONTRACT_ASSIGNMENT");
        notification.put("contractId", contractId);
        notification.put("assignedTo", assignedTo);
        notification.put("timestamp", System.currentTimeMillis());
        
        messagingTemplate.convertAndSend("/topic/user/" + assignedTo + "/assignments", notification);
        messagingTemplate.convertAndSend("/topic/contract-updates", notification);
    }

    @Override
    public void notifyNewBusiness(Long businessId, String businessType) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "NEW_BUSINESS");
        notification.put("businessId", businessId);
        notification.put("businessType", businessType);
        notification.put("timestamp", System.currentTimeMillis());
        
        messagingTemplate.convertAndSend("/topic/business-updates", notification);
    }

    @Override
    public void notifyTaskListUpdate(String username) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "TASK_LIST_UPDATE");
        notification.put("username", username);
        notification.put("timestamp", System.currentTimeMillis());
        
        messagingTemplate.convertAndSend("/topic/user/" + username + "/tasks", notification);
    }

    @Override
    public void notifyNewProcess(Long contractId, String processType, String assignedTo) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "NEW_PROCESS");
        notification.put("contractId", contractId);
        notification.put("processType", processType);
        notification.put("assignedTo", assignedTo);
        notification.put("timestamp", System.currentTimeMillis());
        
        // 发送给指定用户
        messagingTemplate.convertAndSend("/topic/user/" + assignedTo + "/processes", notification);
        // 同时发送到流程更新频道
        messagingTemplate.convertAndSend("/topic/process-updates", notification);
    }

    @Override
    public void notifyProcessStatusUpdate(Long contractId, Long processId, String processType, String status, String assignedTo) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "PROCESS_STATUS_UPDATE");
        notification.put("contractId", contractId);
        notification.put("processId", processId);
        notification.put("processType", processType);
        notification.put("status", status);
        notification.put("assignedTo", assignedTo);
        notification.put("timestamp", System.currentTimeMillis());
        
        // 发送给指定用户
        messagingTemplate.convertAndSend("/topic/user/" + assignedTo + "/processes", notification);
        // 同时发送到流程更新频道
        messagingTemplate.convertAndSend("/topic/process-updates", notification);
    }
} 