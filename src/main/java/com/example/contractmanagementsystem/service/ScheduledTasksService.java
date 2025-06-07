package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.FileUploadProgress;
import com.example.contractmanagementsystem.entity.User;
import com.example.contractmanagementsystem.repository.ContractRepository;
import com.example.contractmanagementsystem.repository.FileUploadProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ScheduledTasksService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledTasksService.class);

    private final FileUploadProgressRepository fileUploadProgressRepository;
    private final ContractRepository contractRepository; // 新增 ContractRepository 依赖
    private final ContractService contractService; // 新增 ContractService 依赖
    private final EmailService emailService; // 新增 EmailService 依赖
    private Path tempUploadPathBase;

    @Value("${file.upload-dir}")
    private String finalUploadDirValue;

    @Autowired
    public ScheduledTasksService(FileUploadProgressRepository fileUploadProgressRepository,
                                 ContractRepository contractRepository,
                                 ContractService contractService,
                                 EmailService emailService) {
        this.fileUploadProgressRepository = fileUploadProgressRepository;
        this.contractRepository = contractRepository;
        this.contractService = contractService;
        this.emailService = emailService;
    }

    @jakarta.annotation.PostConstruct
    private void init() {
        if (finalUploadDirValue == null || finalUploadDirValue.trim().isEmpty()) {
            logger.error("定时任务服务：file.upload-dir 未配置！");
            throw new IllegalStateException("file.upload-dir 属性未配置，无法初始化临时上传路径。");
        }
        this.tempUploadPathBase = Paths.get(finalUploadDirValue).resolve("temp_chunks").toAbsolutePath().normalize();
        logger.info("定时任务服务初始化，临时分块目录基路径设置为: {}", tempUploadPathBase);
    }

    // =================================================================
    // ========= 您原有的清理功能，保持不变，仅为完整性展示 =========
    // =================================================================
    /**
     * 定时清理过期的、未完成的或失败的上传会话及其临时文件。
     * 默认每天凌晨3点执行一次。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredUploads() {
        logger.info("开始执行清理过期上传任务...");
        LocalDateTime expirationTimeInProgress = LocalDateTime.now().minusHours(24);
        LocalDateTime expirationTimeFailed = LocalDateTime.now().minusHours(6);

        List<FileUploadProgress> expiredInProgressUploads =
                fileUploadProgressRepository.findByStatusAndLastChunkUploadedAtBefore("IN_PROGRESS", expirationTimeInProgress);
        if (!expiredInProgressUploads.isEmpty()) {
            logger.info("找到 {} 条创建超过24小时且状态为 IN_PROGRESS 的上传记录需要清理。", expiredInProgressUploads.size());
            processCleanup(expiredInProgressUploads, "IN_PROGRESS (过期)");
        }

        List<FileUploadProgress> failedUploadsChunk =
                fileUploadProgressRepository.findByStatusAndLastChunkUploadedAtBefore("FAILED_CHUNK_UPLOAD", expirationTimeFailed);
        if (!failedUploadsChunk.isEmpty()) {
            logger.info("找到 {} 条状态为 FAILED_CHUNK_UPLOAD 且最后活动超过6小时的上传记录需要清理。", failedUploadsChunk.size());
            processCleanup(failedUploadsChunk, "FAILED_CHUNK_UPLOAD (过期)");
        }

        // ... (其他失败状态的清理逻辑也保持不变) ...

        logger.info("清理过期上传任务执行完毕。");
    }

    private void processCleanup(List<FileUploadProgress> uploadsToClean, String reason) {
        // ... (这个方法的内部实现也完全保持不变) ...
    }

    // =================================================================
    // ========= 新增的邮件提醒功能，与原有功能完全独立 =========
    // =================================================================
    /**
     * 每天凌晨1点执行，检查并更新已过期合同的状态。
     * (此方法从ContractServiceImpl移至此处，集中管理定时任务)
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void updateExpiredContracts() {
        logger.info("开始执行定时任务：更新过期合同状态...");
        int updatedCount = contractService.updateExpiredContractStatuses();
        if (updatedCount > 0) {
            logger.info("定时任务完成：成功更新了 {} 份过期合同的状态。", updatedCount);
        } else {
            logger.info("定时任务完成：没有需要更新状态的过期合同。");
        }
    }

    /**
     * 每天上午9点执行，发送即将到期合同的提醒邮件。
     */
    @Scheduled(cron = "0 0 9 * * ?")
    @Transactional(readOnly = true)
    public void notifyOnExpiringContracts() {
        final int DAYS_TO_EXPIRE = 30; // 定义提醒的时间窗口（例如30天内）
        logger.info("开始执行定时任务：检查并发送 {} 天内即将到期的合同提醒...", DAYS_TO_EXPIRE);

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = LocalDate.now().plusDays(DAYS_TO_EXPIRE);

        List<Contract> expiringContracts = contractRepository.findActiveContractsExpiringBetween(startDate, endDate);

        if (expiringContracts.isEmpty()) {
            logger.info("定时任务完成：没有在 {} 天内即将到期的合同。", DAYS_TO_EXPIRE);
            return;
        }

        logger.info("发现 {} 份即将在 {} 天内到期的合同，准备发送提醒邮件。", expiringContracts.size(), DAYS_TO_EXPIRE);

        for (Contract contract : expiringContracts) {
            User drafter = contract.getDrafter();
            if (drafter != null && StringUtils.hasText(drafter.getEmail())) {
                Map<String, Object> context = new HashMap<>();
                context.put("recipientName", drafter.getRealName() != null ? drafter.getRealName() : drafter.getUsername());
                context.put("taskType", "合同即将到期提醒");
                context.put("contractName", contract.getContractName());

                String actionUrl = "http://localhost:8080/contracts/" + contract.getId() + "/detail";
                context.put("actionUrl", actionUrl);

                String subject = String.format(
                        "【合同管理系统】合同到期提醒: %s 将于 %s 到期",
                        contract.getContractName(),
                        contract.getEndDate().toString()
                );

                emailService.sendHtmlMessage(
                        drafter.getEmail(),
                        subject,
                        "email/task-notification-email",
                        context
                );
            }
        }
        logger.info("定时任务完成：已为 {} 份即将到期的合同发送了提醒邮件。", expiringContracts.size());
    }
}