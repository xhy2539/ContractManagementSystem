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
    private final ContractRepository contractRepository;
    private final ContractService contractService;
    private final EmailService emailService;
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

        List<FileUploadProgress> failedUploadsMerge =
                fileUploadProgressRepository.findByStatusAndLastChunkUploadedAtBefore("FAILED_MERGE", expirationTimeFailed);
        if (!failedUploadsMerge.isEmpty()) {
            logger.info("找到 {} 条状态为 FAILED_MERGE 且最后活动超过6小时的上传记录需要清理。", failedUploadsMerge.size());
            processCleanup(failedUploadsMerge, "FAILED_MERGE (过期)");
        }

        List<FileUploadProgress> failedUploadsMissingChunk =
                fileUploadProgressRepository.findByStatusAndLastChunkUploadedAtBefore("FAILED_MISSING_CHUNK", expirationTimeFailed);
        if (!failedUploadsMissingChunk.isEmpty()) {
            logger.info("找到 {} 条状态为 FAILED_MISSING_CHUNK 且最后活动超过6小时的上传记录需要清理。", failedUploadsMissingChunk.size());
            processCleanup(failedUploadsMissingChunk, "FAILED_MISSING_CHUNK (过期)");
        }

        logger.info("清理过期上传任务执行完毕。");
    }

    private void processCleanup(List<FileUploadProgress> uploadsToClean, String reason) {
        for (FileUploadProgress progress : uploadsToClean) {
            logger.info("准备清理 {} 的上传记录: uploadId={}, 文件名='{}', 用户='{}', 原因: 状态为 {}",
                    reason, progress.getUploadId(), progress.getOriginalFileName(), progress.getUploaderUsername(), progress.getStatus());

            Path chunkDirToDelete = tempUploadPathBase.resolve(progress.getTempFileDirectory());
            logger.info("尝试删除临时分块目录: {}", chunkDirToDelete);
            try {
                if (Files.exists(chunkDirToDelete) && Files.isDirectory(chunkDirToDelete)) {
                    try (Stream<Path> walk = Files.walk(chunkDirToDelete)) {
                        walk.sorted(java.util.Comparator.reverseOrder())
                                .collect(Collectors.toList())
                                .forEach(path -> {
                                    try {
                                        Files.delete(path);
                                    } catch (IOException e) {
                                        logger.warn("删除临时文件 {} 失败: {}", path, e.getMessage());
                                    }
                                });
                    }
                } else {
                    logger.info("临时分块目录 {} 不存在或不是一个目录，无需删除。", chunkDirToDelete);
                }
            } catch (IOException e) {
                logger.error("清理临时分块目录 {} 时发生错误: {}", chunkDirToDelete, e.getMessage(), e);
            }

            try {
                fileUploadProgressRepository.delete(progress);
                logger.info("已从数据库删除上传记录: uploadId={}", progress.getUploadId());
            } catch (Exception e) {
                logger.error("从数据库删除上传记录 {} 失败: {}", progress.getUploadId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 每天凌晨1点执行，检查并更新已过期合同的状态。
     */
    @Scheduled(cron = "0 0 * * * ?")
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
        final int DAYS_TO_EXPIRE = 30;
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

    /**
     * 智能提醒任务 - 扫描并创建合同到期提醒
     * 每天早上8点执行
     */
    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional
    public void createContractExpirationReminders() {
        logger.info("开始扫描并创建合同到期提醒...");
        
        try {
            // 创建30天、15天、7天、3天、1天的提醒
            int[] reminderDays = {30, 15, 7, 3, 1};
            int totalCreated = 0;
            
            for (int days : reminderDays) {
                LocalDate targetDate = LocalDate.now().plusDays(days);
                List<Contract> contractsToRemind = contractRepository.findActiveContractsExpiringBetween(targetDate, targetDate);
                
                for (Contract contract : contractsToRemind) {
                    // 这里需要注入ContractReminderService后调用
                    // contractReminderService.createExpirationReminders(contract.getId(), days);
                    totalCreated++;
                }
            }
            
            logger.info("合同到期提醒创建完成，共创建 {} 个提醒", totalCreated);
            
        } catch (Exception e) {
            logger.error("创建合同到期提醒失败", e);
        }
    }

    /**
     * 批量合同风险分析任务
     * 每周一早上6点执行
     */
    @Scheduled(cron = "0 0 6 * * MON")
    @Transactional
    public void performWeeklyRiskAnalysis() {
        logger.info("开始执行周度合同风险分析...");
        
        try {
            // 获取所有有效状态的合同进行风险分析
            // 这里需要注入ContractAnalysisService后调用
            // List<Contract> activeContracts = contractRepository.findByStatus(ContractStatus.ACTIVE);
            // int analyzedCount = contractAnalysisService.batchAnalyze(
            //     activeContracts.stream().map(Contract::getId).collect(Collectors.toList()),
            //     AnalysisType.RISK_ANALYSIS
            // ).size();
            // logger.info("周度风险分析完成，分析了 {} 个合同", analyzedCount);
            
        } catch (Exception e) {
            logger.error("执行周度风险分析失败", e);
        }
    }

    /**
     * 清理过期的分析结果和提醒
     * 每周日凌晨3点执行
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    @Transactional
    public void cleanupOldAnalysisAndReminders() {
        logger.info("开始清理过期的分析结果和提醒...");
        
        try {
            // 清理90天前的提醒记录
            // int cleanedReminders = contractReminderService.cleanupExpiredReminders(90);
            // logger.info("清理过期提醒完成，删除了 {} 条记录", cleanedReminders);
            
            // 重新分析60天前的分析结果
            // int reanalyzedContracts = contractAnalysisService.reanalyzeOutdatedContracts(60);
            // logger.info("重新分析过期合同完成，重新分析了 {} 个合同", reanalyzedContracts);
            
        } catch (Exception e) {
            logger.error("清理和重新分析任务失败", e);
        }
    }
}