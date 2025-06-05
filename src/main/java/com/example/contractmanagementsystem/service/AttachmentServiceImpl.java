package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.dto.attachment.FileUploadInitiateRequest;
import com.example.contractmanagementsystem.dto.attachment.FileUploadInitiateResponse;
import com.example.contractmanagementsystem.entity.FileUploadProgress;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.repository.FileUploadProgressRepository;

import org.hibernate.Hibernate; // 新增导入，用于某些场景下的显式初始化
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException; // 新增导入
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Service
public class AttachmentServiceImpl implements AttachmentService {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentServiceImpl.class);

    @Value("${file.upload-dir}")
    private String finalUploadDirValue;

    private Path finalUploadPath;
    private Path tempUploadPath;

    private final AuditLogService auditLogService;
    private final FileUploadProgressRepository fileUploadProgressRepository;

    @Autowired
    public AttachmentServiceImpl(AuditLogService auditLogService, FileUploadProgressRepository fileUploadProgressRepository) {
        this.auditLogService = auditLogService;
        this.fileUploadProgressRepository = fileUploadProgressRepository;
    }

    @PostConstruct
    public void initPaths() throws IOException {
        if (!StringUtils.hasText(finalUploadDirValue)) {
            logger.error("最终文件上传目录 'file.upload-dir' 未配置或为空!");
            throw new IllegalStateException("最终文件上传目录 'file.upload-dir' 未配置。");
        }
        this.finalUploadPath = Paths.get(finalUploadDirValue).toAbsolutePath().normalize();
        this.tempUploadPath = this.finalUploadPath.resolve("temp_chunks");

        Files.createDirectories(this.finalUploadPath);
        Files.createDirectories(this.tempUploadPath);

        logger.info("最终附件上传目录: {}", this.finalUploadPath.toString());
        logger.info("临时分块存储目录: {}", this.tempUploadPath.toString());

        if (!Files.isWritable(this.finalUploadPath) || !Files.isWritable(this.tempUploadPath)) {
            logger.error("上传目录不可写。最终目录: {}, 临时目录: {}", this.finalUploadPath, this.tempUploadPath);
            throw new IOException("一个或多个上传目录不可写。请检查权限。");
        }
    }

    @Override
    @Transactional
    public FileUploadInitiateResponse initiateUpload(FileUploadInitiateRequest request, String username) throws IOException {
        // --- 开始替换的代码 ---
        String originalFileName = StringUtils.cleanPath(request.getFileName());

// 1. 获取并格式化当前时间戳
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now());

// 2. 获取一个简短的唯一ID，防止同一秒内上传同名文件
        String shortUuid = UUID.randomUUID().toString().substring(0, 6);

// 3. 安全地处理原始文件名，移除非法字符，保留扩展名
        String fileExtension = "";
        String baseName = originalFileName;
        if (originalFileName.contains(".")) {
            fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
            baseName = originalFileName.substring(0, originalFileName.lastIndexOf("."));
        }
// 将文件名中的非法字符替换为下划线
        String safeBaseName = baseName.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5.\\-_]", "_");
// 限制文件名长度，避免过长
        if (safeBaseName.length() > 50) {
            safeBaseName = safeBaseName.substring(0, 50);
        }

// 4. 拼接成新的、信息丰富的服务器端文件名
        String serverSideFileName = String.format("%s_%s_%s%s", safeBaseName, timestamp, shortUuid, fileExtension);
// --- 结束替换的代码 ---
        String uploadId = UUID.randomUUID().toString();
        String tempSubDirName = uploadId;
        Path chunkDirForThisUpload = this.tempUploadPath.resolve(tempSubDirName);
        Files.createDirectories(chunkDirForThisUpload);

        FileUploadProgress progress = new FileUploadProgress(
                uploadId,
                originalFileName,
                serverSideFileName,
                request.getTotalSize(),
                username,
                tempSubDirName
        );
        progress.setStatus("IN_PROGRESS");
        fileUploadProgressRepository.save(progress);

        logger.info("用户 '{}' 初始化上传: uploadId={}, 文件名='{}', 服务器端文件名='{}', 大小={}", username, uploadId, originalFileName, serverSideFileName, request.getTotalSize());
        auditLogService.logAction(username, "ATTACHMENT_UPLOAD_INITIATED", "初始化附件上传: " + originalFileName + ", uploadId: " + uploadId);

        return new FileUploadInitiateResponse(uploadId, serverSideFileName);
    }

    @Override
    @Transactional
    public void uploadChunk(String uploadId, int chunkNumber, int totalChunks, MultipartFile fileChunk, String username) throws IOException {
        FileUploadProgress progress = fileUploadProgressRepository.findByUploadIdAndUploaderUsername(uploadId, username)
                .orElseThrow(() -> new ResourceNotFoundException("上传会话ID无效或您无权操作: " + uploadId));

        if (!"IN_PROGRESS".equals(progress.getStatus())) {
            logger.warn("用户 '{}' 尝试向已完成/失败的上传 (ID: {}, 状态: {}) 添加分块。", username, uploadId, progress.getStatus());
            throw new BusinessLogicException("此上传会话当前状态为 " + progress.getStatus() + "，不允许添加分块。");
        }

        if (progress.getTotalChunks() == null || progress.getTotalChunks() == 0) {
            progress.setTotalChunks(totalChunks);
        } else if (progress.getTotalChunks() != totalChunks && totalChunks > 0) {
            logger.warn("用户 '{}' 上传分块时提供的总块数 ({}) 与记录的总块数 ({}) 不一致 (uploadId: {})。将使用新值。", username, totalChunks, progress.getTotalChunks(), uploadId);
            progress.setTotalChunks(totalChunks);
        }

        Path chunkDir = this.tempUploadPath.resolve(progress.getTempFileDirectory());
        Files.createDirectories(chunkDir);
        Path chunkFile = chunkDir.resolve(String.valueOf(chunkNumber));

        try {
            Files.copy(fileChunk.getInputStream(), chunkFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            progress.addUploadedChunk(chunkNumber);
            fileUploadProgressRepository.save(progress);
            logger.info("用户 '{}' 上传分块成功: uploadId={}, 文件='{}', 分块 {}/{}", username, uploadId, progress.getOriginalFileName(), chunkNumber + 1, progress.getTotalChunks());
        } catch (IOException e) {
            logger.error("用户 '{}' 上传分块失败: uploadId={}, 文件='{}', 分块 {}, 错误: {}", username, uploadId, progress.getOriginalFileName(), chunkNumber, e.getMessage(), e);
            progress.setStatus("FAILED_CHUNK_UPLOAD");
            fileUploadProgressRepository.save(progress);
            throw e;
        }
    }

    @Override
    @Transactional
    public String finalizeUpload(String uploadId, String clientProvidedOriginalFileName, String username) throws IOException {
        FileUploadProgress progress = fileUploadProgressRepository.findByUploadIdAndUploaderUsername(uploadId, username)
                .orElseThrow(() -> new ResourceNotFoundException("上传会话ID无效或您无权操作: " + uploadId));

        if (!"IN_PROGRESS".equals(progress.getStatus())) {
            if ("COMPLETED".equals(progress.getStatus())) {
                logger.info("上传 {} (用户: {}) 已完成，重复的完成请求。返回现有文件名: {}", uploadId, username, progress.getServerSideFileName());
                return progress.getServerSideFileName();
            }
            logger.warn("用户 '{}' 尝试完成非进行中状态的上传 (ID: {}, 状态: {})。", username, uploadId, progress.getStatus());
            throw new BusinessLogicException("此上传会话当前状态为 " + progress.getStatus() + "，无法完成。");
        }
        if (clientProvidedOriginalFileName != null && !clientProvidedOriginalFileName.equals(progress.getOriginalFileName())) {
            logger.warn("用户 '{}' 完成上传时提供的原始文件名 '{}' 与记录中的 '{}' 不符 (uploadId: {})。将使用记录中的原始文件名。",
                    username, clientProvidedOriginalFileName, progress.getOriginalFileName(), uploadId);
        }

        if (!progress.areAllChunksUploaded()) {
            logger.warn("尝试完成未完成的上传: uploadId={}, 已上传 {}/{} 块 (用户: {})", uploadId, progress.getUploadedChunks().size(), progress.getTotalChunks(), username);
            throw new BusinessLogicException("文件尚未完全上传。已上传 " + progress.getUploadedChunks().size() + " / " + (progress.getTotalChunks() != null ? progress.getTotalChunks() : "?") + " 块。");
        }

        String finalFileName = progress.getServerSideFileName();
        Path finalFilePath = this.finalUploadPath.resolve(finalFileName).normalize();

        if (!finalFilePath.startsWith(this.finalUploadPath)) {
            logger.error("最终文件路径 '{}' 超出了允许的上传目录 '{}'。", finalFilePath, this.finalUploadPath);
            throw new BusinessLogicException("无效的文件名或路径。");
        }

        Path chunkDir = this.tempUploadPath.resolve(progress.getTempFileDirectory());

        try (OutputStream os = Files.newOutputStream(finalFilePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            for (int i = 0; i < progress.getTotalChunks(); i++) {
                Path chunkFile = chunkDir.resolve(String.valueOf(i));
                if (!Files.exists(chunkFile)) {
                    logger.error("分块丢失: uploadId={}, 文件='{}', 期望的分块 {}", uploadId, progress.getOriginalFileName(), i);
                    progress.setStatus("FAILED_MISSING_CHUNK");
                    fileUploadProgressRepository.save(progress);
                    throw new ResourceNotFoundException("合并文件失败：分块 " + i + " 未找到。");
                }
                Files.copy(chunkFile, os);
            }
        } catch (IOException e) {
            logger.error("合并文件失败: uploadId={}, 文件='{}', 错误: {}", uploadId, progress.getOriginalFileName(), e.getMessage(), e);
            progress.setStatus("FAILED_MERGE");
            fileUploadProgressRepository.save(progress);
            Files.deleteIfExists(finalFilePath);
            throw new IOException("合并文件分块时出错: " + e.getMessage(), e);
        }

        try (Stream<Path> walk = Files.walk(chunkDir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            logger.warn("删除临时文件/目录 {} 失败: {}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            logger.warn("清理临时分块目录 {} 失败: {}", chunkDir, e.getMessage());
        }

        progress.setStatus("COMPLETED");
        progress.setLastChunkUploadedAt(LocalDateTime.now());
        fileUploadProgressRepository.save(progress);

        logger.info("用户 '{}' 完成文件上传: uploadId={}, 原始文件名='{}', 最终保存为='{}'", username, uploadId, progress.getOriginalFileName(), finalFileName);
        auditLogService.logAction(username, "ATTACHMENT_UPLOAD_COMPLETED", "附件上传完成: " + progress.getOriginalFileName() + ", 保存为: " + finalFileName);

        return finalFileName;
    }

    @Override
    public Path getAttachment(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new BusinessLogicException("请求的附件文件名不能为空。");
        }
        String cleanedFilename = StringUtils.cleanPath(filename);
        if (cleanedFilename.contains("..") || cleanedFilename.startsWith("/") || cleanedFilename.startsWith("\\")) {
            throw new BusinessLogicException("附件文件名包含无效字符或路径。");
        }
        Path filePath = this.finalUploadPath.resolve(cleanedFilename).normalize();
        if (!filePath.startsWith(this.finalUploadPath)) {
            throw new BusinessLogicException("试图访问上传目录之外的文件。");
        }
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new ResourceNotFoundException("附件文件不存在或不可读: " + cleanedFilename);
        }
        return filePath;
    }

    @Override
    @Transactional
    public void deleteUploadedFile(String serverFileName, String username) throws IOException {
        if (!StringUtils.hasText(serverFileName)) {
            throw new BusinessLogicException("要删除的附件文件名不能为空。");
        }

        Path filePathToDelete = this.finalUploadPath.resolve(StringUtils.cleanPath(serverFileName)).normalize();

        if (!filePathToDelete.startsWith(this.finalUploadPath)) {
            logger.warn("用户 '{}' 尝试删除最终目录之外的文件: {}", username, filePathToDelete);
            throw new BusinessLogicException("非法的文件路径，无法删除。");
        }

        FileUploadProgress progress = fileUploadProgressRepository.findByServerSideFileNameAndUploaderUsername(serverFileName, username)
                .orElseGet(() -> fileUploadProgressRepository.findByServerSideFileName(serverFileName)
                        .orElse(null));

        boolean fileDeleted = false;
        if (Files.exists(filePathToDelete)) {
            try {
                Files.delete(filePathToDelete);
                fileDeleted = true;
                logger.info("用户 '{}' 已成功删除附件物理文件: {}", username, serverFileName);
                auditLogService.logAction(username, "ATTACHMENT_FILE_DELETED", "删除附件物理文件: " + serverFileName);
            } catch (IOException e) {
                logger.error("用户 '{}' 删除附件物理文件 {} 失败: {}", username, serverFileName, e.getMessage(), e);
                throw new IOException("删除附件物理文件失败: " + serverFileName, e);
            }
        } else {
            logger.warn("用户 '{}' 尝试删除的附件物理文件不存在: {}", username, serverFileName);
        }

        if (progress != null) {
            logger.info("找到附件 '{}' 的上传进度记录 (UploadId: {}), 准备清理。", serverFileName, progress.getUploadId());
            Path chunkDir = this.tempUploadPath.resolve(progress.getTempFileDirectory());
            if (Files.exists(chunkDir) && Files.isDirectory(chunkDir)) {
                logger.info("尝试删除附件 '{}' 的临时分块目录: {}", serverFileName, chunkDir);
                try (Stream<Path> walk = Files.walk(chunkDir)) {
                    walk.sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                    logger.debug("已删除临时文件/目录: {}", path);
                                } catch (IOException e) {
                                    logger.warn("删除临时分块 {} 失败: {}", path, e.getMessage());
                                }
                            });
                    logger.info("临时分块目录 {} 内容已删除。", chunkDir);
                } catch (IOException e) {
                    logger.warn("遍历临时分块目录 {} 失败: {}", chunkDir, e.getMessage());
                }
            } else {
                logger.info("附件 '{}' 的临时分块目录 {} 不存在或不是目录。", serverFileName, chunkDir);
            }

            try {
                fileUploadProgressRepository.delete(progress);
                logger.info("已从数据库删除附件 '{}' (UploadId: {}) 的上传进度记录。", serverFileName, progress.getUploadId());
                auditLogService.logAction(username, "ATTACHMENT_RECORD_DELETED", "删除附件上传记录: " + serverFileName + ", UploadId: " + progress.getUploadId());
            } catch (Exception e) {
                logger.error("从数据库删除附件 '{}' (UploadId: {}) 的上传进度记录失败: {}", serverFileName, progress.getUploadId(), e.getMessage(), e);
            }
        } else {
            logger.warn("未找到附件 '{}' 对应的上传进度记录。可能已被清理或从未完整记录。", serverFileName);
            if (fileDeleted) {
                auditLogService.logAction(username, "ATTACHMENT_FILE_DELETED_NO_RECORD", "删除了附件物理文件 " + serverFileName + "，但未找到其上传记录。");
            }
        }
    }

    // 新增方法实现
    @Override
    @Transactional(readOnly = true)
    public FileUploadProgress getUploadProgressDetails(String uploadId, String username) throws ResourceNotFoundException, AccessDeniedException {
        FileUploadProgress progress = fileUploadProgressRepository.findById(uploadId) // FileUploadProgress 的主键是 uploadId (String)
                .orElseThrow(() -> new ResourceNotFoundException("上传ID '" + uploadId + "' 未找到。"));

        // 验证操作用户是否为该上传的发起者
        if (!progress.getUploaderUsername().equals(username)) {
            // 此处可以根据业务逻辑决定是否允许非上传者（例如管理员）查看，
            // 但对于前端恢复上传状态，通常只应允许原上传者。
            logger.warn("用户 '{}' 尝试访问不属于自己的上传记录 (ID: {}, 属于: {})。", username, uploadId, progress.getUploaderUsername());
            throw new AccessDeniedException("您无权查看此上传 (ID: " + uploadId + ") 的状态。");
        }

        // FileUploadProgress 中的 uploadedChunks 字段是 EAGER fetch，所以不需要显式初始化。
        // 如果它是 LAZY，则需要： Hibernate.initialize(progress.getUploadedChunks());
        return progress;
    }
}