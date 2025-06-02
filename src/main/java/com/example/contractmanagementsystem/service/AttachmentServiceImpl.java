package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.dto.attachment.FileUploadInitiateRequest;
import com.example.contractmanagementsystem.dto.attachment.FileUploadInitiateResponse;
import com.example.contractmanagementsystem.entity.FileUploadProgress; // 新增导入
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.repository.FileUploadProgressRepository;
import com.example.contractmanagementsystem.service.AttachmentService;
import com.example.contractmanagementsystem.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // 确保事务注解
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
    private final FileUploadProgressRepository fileUploadProgressRepository; // 替换内存Map

    @Autowired
    public AttachmentServiceImpl(AuditLogService auditLogService, FileUploadProgressRepository fileUploadProgressRepository) {
        this.auditLogService = auditLogService;
        this.fileUploadProgressRepository = fileUploadProgressRepository; // 注入Repository
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
    @Transactional // 事务管理
    public FileUploadInitiateResponse initiateUpload(FileUploadInitiateRequest request, String username) throws IOException {
        String originalFileName = StringUtils.cleanPath(request.getFileName());
        String fileExtension = "";
        if (originalFileName.contains(".")) {
            fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        String serverSideFileName = UUID.randomUUID().toString() + fileExtension;
        String uploadId = UUID.randomUUID().toString();

        // 临时分块的子目录名，可以用uploadId本身
        String tempSubDirName = uploadId;
        Path chunkDirForThisUpload = this.tempUploadPath.resolve(tempSubDirName);
        Files.createDirectories(chunkDirForThisUpload); // 为本次上传创建独立的临时分块目录

        FileUploadProgress progress = new FileUploadProgress(
                uploadId,
                originalFileName,
                serverSideFileName,
                request.getTotalSize(),
                username,
                tempSubDirName // 存储相对路径或标识符
        );
        progress.setStatus("IN_PROGRESS");
        // totalChunks 可以在第一个块上传时确定或由客户端在初始化时提供（如果已知）
        // progress.setTotalChunks(request.getTotalChunks()); // 如果客户端提供

        fileUploadProgressRepository.save(progress);

        logger.info("用户 '{}' 初始化上传: uploadId={}, 文件名='{}', 服务器端文件名='{}', 大小={}", username, uploadId, originalFileName, serverSideFileName, request.getTotalSize());
        auditLogService.logAction(username, "ATTACHMENT_UPLOAD_INITIATED", "初始化附件上传: " + originalFileName + ", uploadId: " + uploadId);

        return new FileUploadInitiateResponse(uploadId, serverSideFileName);
    }

    @Override
    @Transactional // 事务管理
    public void uploadChunk(String uploadId, int chunkNumber, int totalChunks, MultipartFile fileChunk, String username) throws IOException {
        FileUploadProgress progress = fileUploadProgressRepository.findByUploadIdAndUploaderUsername(uploadId, username)
                .orElseThrow(() -> new ResourceNotFoundException("上传会话ID无效或您无权操作: " + uploadId));

        if (!"IN_PROGRESS".equals(progress.getStatus())) {
            logger.warn("用户 '{}' 尝试向已完成/失败的上传 (ID: {}, 状态: {}) 添加分块。", username, uploadId, progress.getStatus());
            throw new BusinessLogicException("此上传会话当前状态为 " + progress.getStatus() + "，不允许添加分块。");
        }

        // 更新总块数 (客户端应该在每次上传块时都带上总块数，以防第一次初始化时未知)
        if (progress.getTotalChunks() == null || progress.getTotalChunks() != totalChunks) {
            progress.setTotalChunks(totalChunks);
        }

        Path chunkDir = this.tempUploadPath.resolve(progress.getTempFileDirectory()); // 使用记录的临时目录
        Files.createDirectories(chunkDir); // 确保目录存在
        Path chunkFile = chunkDir.resolve(String.valueOf(chunkNumber));

        try {
            Files.copy(fileChunk.getInputStream(), chunkFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            progress.addUploadedChunk(chunkNumber); // 会更新 lastChunkUploadedAt
            fileUploadProgressRepository.save(progress);
            logger.info("用户 '{}' 上传分块成功: uploadId={}, 文件='{}', 分块 {}/{}", username, uploadId, progress.getOriginalFileName(), chunkNumber + 1, totalChunks);
        } catch (IOException e) {
            logger.error("用户 '{}' 上传分块失败: uploadId={}, 文件='{}', 分块 {}, 错误: {}", username, uploadId, progress.getOriginalFileName(), chunkNumber, e.getMessage(), e);
            progress.setStatus("FAILED_CHUNK_UPLOAD"); // 可以设置一个更具体的状态
            fileUploadProgressRepository.save(progress);
            throw e;
        }
    }

    @Override
    @Transactional // 事务管理
    public String finalizeUpload(String uploadId, String clientProvidedFileName, String username) throws IOException {
        // clientProvidedFileName 在我们的实现中实际上是 progress.getServerSideFileName()，因为我们不信任客户端提供的最终文件名
        FileUploadProgress progress = fileUploadProgressRepository.findByUploadIdAndUploaderUsername(uploadId, username)
                .orElseThrow(() -> new ResourceNotFoundException("上传会话ID无效或您无权操作: " + uploadId));

        if (!"IN_PROGRESS".equals(progress.getStatus())) {
            // 如果已经是COMPLETED，可能表示重复调用，可以考虑直接返回文件名或抛异常
            if ("COMPLETED".equals(progress.getStatus())) {
                logger.info("上传 {} (用户: {}) 已完成，重复的完成请求。", uploadId, username);
                return progress.getServerSideFileName();
            }
            logger.warn("用户 '{}' 尝试完成非进行中状态的上传 (ID: {}, 状态: {})。", username, uploadId, progress.getStatus());
            throw new BusinessLogicException("此上传会话当前状态为 " + progress.getStatus() + "，无法完成。");
        }

        if (!progress.areAllChunksUploaded()) {
            logger.warn("尝试完成未完成的上传: uploadId={}, 已上传 {}/{} 块 (用户: {})", uploadId, progress.getUploadedChunks().size(), progress.getTotalChunks(), username);
            throw new BusinessLogicException("文件尚未完全上传。已上传 " + progress.getUploadedChunks().size() + " / " + progress.getTotalChunks() + " 块。");
        }

        String finalFileName = progress.getServerSideFileName();
        Path finalFilePath = this.finalUploadPath.resolve(finalFileName).normalize();

        if (!finalFilePath.startsWith(this.finalUploadPath)) {
            logger.error("最终文件路径 '{}' 超出了允许的上传目录 '{}'。", finalFilePath, this.finalUploadPath);
            throw new BusinessLogicException("无效的文件名或路径。");
        }

        Path chunkDir = this.tempUploadPath.resolve(progress.getTempFileDirectory()); // 使用记录的临时目录

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
            Files.deleteIfExists(finalFilePath); // 尝试删除可能已部分创建的最终文件
            throw new IOException("合并文件分块时出错: " + e.getMessage(), e);
        }

        // 清理临时分块文件和目录
        try (Stream<Path> walk = Files.walk(chunkDir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .collect(Collectors.toList())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            logger.warn("删除临时文件/目录 {} 失败: {}", path, e.getMessage());
                        }
                    });
            Files.deleteIfExists(chunkDir); // 删除空的 uploadId 目录
        } catch (IOException e) {
            logger.warn("清理临时分块目录 {} 失败: {}", chunkDir, e.getMessage());
        }

        progress.setStatus("COMPLETED");
        progress.setLastChunkUploadedAt(LocalDateTime.now()); // 明确记录完成时间
        fileUploadProgressRepository.save(progress);

        logger.info("用户 '{}' 完成文件上传: uploadId={}, 原始文件名='{}', 最终保存为='{}'", username, uploadId, progress.getOriginalFileName(), finalFileName);
        auditLogService.logAction(username, "ATTACHMENT_UPLOAD_COMPLETED", "附件上传完成: " + progress.getOriginalFileName() + ", 保存为: " + finalFileName);

        return finalFileName;
    }

    @Override
    public Path getAttachment(String filename) {
        // 此方法保持不变，因为它操作的是最终存储目录
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

    // 你可能还需要一个后台任务来清理长时间未完成或失败的上传记录及其对应的临时文件
    // 例如，可以每天运行一次，删除那些状态为 IN_PROGRESS 但 lastChunkUploadedAt 超过24小时的记录和文件
}