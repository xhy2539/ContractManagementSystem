package com.example.contractmanagementsystem.service.impl;

import com.example.contractmanagementsystem.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Autowired
    public EmailServiceImpl(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Override
    @Async
    public void sendSimpleMessage(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            logger.info("成功发送简单文本邮件至 {}", to);
        } catch (Exception e) {
            logger.error("发送简单文本邮件至 {} 时发生错误", to, e);
        }
    }

    @Override
    @Async
    public void sendHtmlMessage(String to, String subject, String templateName, Map<String, Object> context) {
        try {
            Context thymeleafContext = new Context();
            thymeleafContext.setVariables(context);

            String htmlContent = templateEngine.process( templateName, thymeleafContext);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true表示这是一个HTML邮件

            mailSender.send(mimeMessage);
            logger.info("成功发送HTML模板邮件至 {}", to);
        } catch (MessagingException e) {
            logger.error("发送HTML模板邮件至 {} 时发生邮件协议错误", to, e);
        } catch (Exception e) {
            logger.error("发送HTML模板邮件至 {} 时发生未知错误", to, e);
        }
    }
}