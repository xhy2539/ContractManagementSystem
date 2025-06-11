package com.example.contractmanagementsystem.service;

import java.util.Map;

public interface EmailService {

    /**
     * 发送一个简单的文本邮件
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param text 邮件内容
     */
    void sendSimpleMessage(String to, String subject, String text);

    /**
     * 发送一个基于模板的HTML邮件
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param templateName 邮件模板的名称 (例如 "task-notification-email")
     * @param context 包含模板中所需变量的Map
     */
    void sendHtmlMessage(String to, String subject, String templateName, Map<String, Object> context);
}