package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.attachment.FileUploadInitiateRequest;
import com.example.contractmanagementsystem.dto.attachment.FileUploadInitiateResponse;

import com.example.contractmanagementsystem.dto.attachment.ChunkUploadResponse;
import com.example.contractmanagementsystem.service.AttachmentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.core.io.Resource; // 用于文件下载
import org.springframework.core.io.UrlResource; // 用于文件下载
import org.springframework.http.HttpHeaders; // 用于文件下载

@RestController
@RequestMapping("/api/attachments") // API基础路径
public class AttachmentController {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentController.class);
    private final AttachmentService attachmentService;

    @Autowired
    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /**
     * 端点：初始化文件上传
     * POST /api/attachments/initiate
     */
    @PostMapping("/initiate")
    @PreAuthorize("isAuthenticated()") // 假设所有认证用户都可以尝试初始化上传
    public ResponseEntity<FileUploadInitiateResponse> initiateUpload(
            @Valid @RequestBody FileUploadInitiateRequest initiateRequest,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            String username = authentication.getName();
            if (username == null) {
                throw new UsernameNotFoundException("无法获取当前认证的用户名。");
            }
            FileUploadInitiateResponse response = attachmentService.initiateUpload(initiateRequest, username);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            logger.error("初始化上传失败 (用户: {}): {}", authentication != null ? authentication.getName() : "N/A", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null); // 避免在响应体中暴露过多错误细节
        } catch (Exception e) {
            logger.error("初始化上传时发生未知错误 (用户: {}): {}", authentication != null ? authentication.getName() : "N/A", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null); // 例如 DTO 校验失败或其他业务逻辑异常
        }
    }

    /**
     * 端点：上传文件分块
     * POST /api/attachments/upload-chunk
     * 参数:
     * - uploadId (请求头或路径变量)
     * - chunkNumber (请求参数)
     * - totalChunks (请求参数)
     * - file (MultipartFile)
     */
    @PostMapping("/upload-chunk")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> uploadChunk(
            @RequestHeader("X-Upload-Id") String uploadId, // 从请求头获取uploadId
            @RequestParam("chunkNumber") int chunkNumber,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("file") MultipartFile fileChunk,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户未认证");
            }
            String username = authentication.getName();
            if (username == null) {
                throw new UsernameNotFoundException("无法获取当前认证的用户名。");
            }

            if (fileChunk.isEmpty()) {
                return ResponseEntity.badRequest().body("上传的分块不能为空");
            }

            attachmentService.uploadChunk(uploadId, chunkNumber, totalChunks, fileChunk, username);
            // 为了简化，成功时返回200 OK，客户端可以根据此响应继续上传下一个块
            // 如果需要更详细的响应，可以使用 ChunkUploadResponse DTO
            return ResponseEntity.ok("分块 " + (chunkNumber + 1) + "/" + totalChunks + " 上传成功。");

        } catch (IOException e) {
            logger.error("上传分块 {} 失败 (uploadId: {}, 用户: {}): {}", chunkNumber, uploadId, authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("分块上传失败: " + e.getMessage());
        } catch (Exception e) { // 捕获 ResourceNotFoundException, BusinessLogicException 等
            logger.error("上传分块 {} 时发生错误 (uploadId: {}, 用户: {}): {}", chunkNumber, uploadId, authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * 端点：完成文件上传并合并分块
     * POST /api/attachments/finalize/{uploadId}
     * 参数:
     * - originalFileName (请求参数，虽然我们可能主要使用服务器端生成的名字，但客户端仍需告知原始名用于日志等)
     */
    @PostMapping("/finalize/{uploadId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> finalizeUpload(
            @PathVariable String uploadId,
            @RequestParam("originalFileName") String originalFileName, // 客户端告知的原始文件名
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            String username = authentication.getName();
            if (username == null) {
                throw new UsernameNotFoundException("无法获取当前认证的用户名。");
            }

            String finalSavedFileName = attachmentService.finalizeUpload(uploadId, originalFileName, username);

            // 返回一个包含最终文件名的JSON对象，方便前端使用
            Map<String, String> response = new java.util.HashMap<>();
            response.put("message", "文件上传并合并成功。");
            response.put("fileName", finalSavedFileName); // 这是服务器端保存的实际文件名
            response.put("uploadId", uploadId); // 确认是哪个上传完成了

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            logger.error("完成上传 {} (原始文件名: {}, 用户: {}) 失败: {}", uploadId, originalFileName, authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "完成上传失败: " + e.getMessage()));
        } catch (Exception e) { // 捕获 ResourceNotFoundException, BusinessLogicException 等
            logger.error("完成上传 {} (原始文件名: {}, 用户: {}) 时发生错误: {}", uploadId, originalFileName, authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 端点：下载附件 (这个端点可以从 ContractController 迁移过来或保持独立)
     * GET /api/attachments/download/{filename:.+}
     * 使用 PathVariable 正则表达式来确保filename可以包含点号.
     */
    @GetMapping("/download/{filename:.+}")
    // 权限可以根据具体业务调整，例如 isAuthenticated() 或特定权限
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable String filename, Authentication authentication) {
        try {
            // 假设 attachmentService.getAttachment(filename) 返回文件的 Path
            Path filePath = attachmentService.getAttachment(filename);
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                // 记录下载行为
                if (authentication != null && authentication.getName() != null) {
                    // auditLogService.logAction(authentication.getName(), "ATTACHMENT_DOWNLOADED", "下载附件: " + filename);
                    logger.info("用户 '{}' 下载附件: {}", authentication.getName(), filename);
                } else {
                    logger.info("匿名用户尝试下载附件 (已通过认证检查，但principal名称为空): {}", filename);
                }

                String contentType = Files.probeContentType(filePath);
                if (contentType == null) {
                    contentType = "application/octet-stream"; // 默认类型
                }

                return ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                logger.warn("请求下载的附件不存在或不可读: {}", filename);
                throw new RuntimeException("无法读取文件: " + filename);
            }
        } catch (Exception e) {
            logger.error("下载附件 {} 失败: {}", filename, e.getMessage(), e);
            // 根据异常类型返回不同的状态码，例如 ResourceNotFoundException -> 404
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }
}