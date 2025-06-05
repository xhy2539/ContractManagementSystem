package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.FileUploadProgress;
import com.example.contractmanagementsystem.repository.FileUploadProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ScheduledTasksService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledTasksService.class);

    private final FileUploadProgressRepository fileUploadProgressRepository;
    private Path tempUploadPathBase; // 基础临时上传路径，例如 /path/to/uploads/temp_chunks

    // 从 application.properties 读取上传目录基路径
    @Value("${file.upload-dir}")
    private String finalUploadDirValue;


    @Autowired
    public ScheduledTasksService(FileUploadProgressRepository fileUploadProgressRepository) {
        this.fileUploadProgressRepository = fileUploadProgressRepository;
        // 初始化 tempUploadPathBase (确保在构造函数或 @PostConstruct 中进行)
        // 这里假设 finalUploadDirValue 已经通过 @Value 注入
        // 注意：@Value 的注入可能在构造函数执行时尚未完成，所以移到 @PostConstruct 或实际使用前初始化
        this.tempUploadPathBase = null; // 将在 init 方法中初始化
    }

    @jakarta.annotation.PostConstruct
    private void init() {
        if (finalUploadDirValue == null || finalUploadDirValue.trim().isEmpty()) {
            logger.error("定时任务服务：file.upload-dir 未配置！");
            throw new IllegalStateException("file.upload-dir 属性未配置，无法初始化临时上传路径。");
        }
        // tempUploadPathBase 指向 temp_chunks 目录
        this.tempUploadPathBase = Paths.get(finalUploadDirValue).resolve("temp_chunks").toAbsolutePath().normalize();
        logger.info("定时任务服务初始化，临时分块目录基路径设置为: {}", tempUploadPathBase);
    }

    /**
     * 定时清理过期的、未完成的或失败的上传会话及其临时文件。
     * 默认每天凌晨3点执行一次。
     * cron表达式: 秒 分 时 日 月 周 (年 - 可选)
     * "0 0 3 * * ?" 表示每天的3点0分0秒执行
     */
    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨3点执行
    // @Scheduled(fixedRate = 3600000) // 或者每小时执行一次 (3600000毫秒) 用于测试
    @Transactional
    public void cleanupExpiredUploads() {
        logger.info("开始执行清理过期上传任务...");

        // 定义过期时间，例如24小时前
        LocalDateTime expirationTimeInProgress = LocalDateTime.now().minusHours(24);
        // 对于失败的上传，可以设置一个较短的保留时间，例如6小时
        LocalDateTime expirationTimeFailed = LocalDateTime.now().minusHours(6);

        // 1. 查找长时间处于 IN_PROGRESS 状态的上传
        List<FileUploadProgress> expiredInProgressUploads =
                fileUploadProgressRepository.findByStatusAndLastChunkUploadedAtBefore("IN_PROGRESS", expirationTimeInProgress);

        if (expiredInProgressUploads.isEmpty()) {
            logger.info("没有找到创建超过24小时且状态为 IN_PROGRESS 的上传记录需要清理。");
        } else {
            logger.info("找到 {} 条创建超过24小时且状态为 IN_PROGRESS 的上传记录需要清理。", expiredInProgressUploads.size());
            processCleanup(expiredInProgressUploads, "IN_PROGRESS (过期)");
        }


        // 2. 查找状态为 FAILED (各种失败状态) 且超过一定时间的上传
        List<FileUploadProgress> failedUploadsChunk =
                fileUploadProgressRepository.findByStatusAndLastChunkUploadedAtBefore("FAILED_CHUNK_UPLOAD", expirationTimeFailed);
        List<FileUploadProgress> failedUploadsMerge =
                fileUploadProgressRepository.findByStatusAndLastChunkUploadedAtBefore("FAILED_MERGE", expirationTimeFailed);
        List<FileUploadProgress> failedUploadsMissingChunk =
                fileUploadProgressRepository.findByStatusAndLastChunkUploadedAtBefore("FAILED_MISSING_CHUNK", expirationTimeFailed);


        if (!failedUploadsChunk.isEmpty()) {
            logger.info("找到 {} 条状态为 FAILED_CHUNK_UPLOAD 且最后活动超过6小时的上传记录需要清理。", failedUploadsChunk.size());
            processCleanup(failedUploadsChunk, "FAILED_CHUNK_UPLOAD (过期)");
        }
        if (!failedUploadsMerge.isEmpty()) {
            logger.info("找到 {} 条状态为 FAILED_MERGE 且最后活动超过6小时的上传记录需要清理。", failedUploadsMerge.size());
            processCleanup(failedUploadsMerge, "FAILED_MERGE (过期)");
        }
        if (!failedUploadsMissingChunk.isEmpty()) {
            logger.info("找到 {} 条状态为 FAILED_MISSING_CHUNK 且最后活动超过6小时的上传记录需要清理。", failedUploadsMissingChunk.size());
            processCleanup(failedUploadsMissingChunk, "FAILED_MISSING_CHUNK (过期)");
        }

        if (failedUploadsChunk.isEmpty() && failedUploadsMerge.isEmpty() && failedUploadsMissingChunk.isEmpty()) {
            logger.info("没有找到状态为 FAILED_* 且最后活动超过6小时的上传记录需要清理。");
        }

        logger.info("清理过期上传任务执行完毕。");
    }

    private void processCleanup(List<FileUploadProgress> uploadsToClean, String reason) {
        for (FileUploadProgress progress : uploadsToClean) {
            logger.info("准备清理 {} 的上传记录: uploadId={}, 文件名='{}', 用户='{}', 原因: 状态为 {}",
                    reason, progress.getUploadId(), progress.getOriginalFileName(), progress.getUploaderUsername(), progress.getStatus());

            // 1. 删除对应的临时分块目录
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
                                        logger.debug("已删除临时文件/目录: {}", path);
                                    } catch (IOException e) {
                                        logger.warn("删除临时文件 {} 失败: {}", path, e.getMessage());
                                    }
                                });
                    }
                    // 再次尝试删除空的父目录
                    if (Files.isDirectory(chunkDirToDelete) && Files.list(chunkDirToDelete).findAny().isEmpty()) {
                        Files.deleteIfExists(chunkDirToDelete);
                        logger.info("已成功删除空的临时分块目录: {}", chunkDirToDelete);
                    } else if (Files.exists(chunkDirToDelete)) {
                        logger.warn("临时分块目录 {} 未能完全删除，可能仍有文件或子目录。", chunkDirToDelete);
                    }

                } else {
                    logger.info("临时分块目录 {} 不存在或不是一个目录，无需删除。", chunkDirToDelete);
                }
            } catch (IOException e) {
                logger.error("清理临时分块目录 {} 时发生错误: {}", chunkDirToDelete, e.getMessage(), e);
            }

            // 2. 从数据库中删除该上传记录
            try {
                fileUploadProgressRepository.delete(progress);
                logger.info("已从数据库删除上传记录: uploadId={}", progress.getUploadId());
            } catch (Exception e) {
                logger.error("从数据库删除上传记录 {} 失败: {}", progress.getUploadId(), e.getMessage(), e);
            }
        }
    }
}