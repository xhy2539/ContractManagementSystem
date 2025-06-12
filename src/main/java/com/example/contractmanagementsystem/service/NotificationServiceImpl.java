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
        notification.put("contractId", contractId);
        notification.put("status", status);
        notification.put("timestamp", System.currentTimeMillis());
        
        // 发送到 /topic/contract-status 主题
        messagingTemplate.convertAndSend("/topic/contract-status", notification);
    }
} 