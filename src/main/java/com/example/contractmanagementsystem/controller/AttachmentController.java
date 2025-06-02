package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.attachment.FileUploadInitiateRequest;
import com.example.contractmanagementsystem.dto.attachment.FileUploadInitiateResponse;
// ChunkUploadResponse is not directly used as a return type in current methods, can be removed if not planned for future
// import com.example.contractmanagementsystem.dto.attachment.ChunkUploadResponse;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
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
import java.util.HashMap; // Import HashMap
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentController.class);
    private final AttachmentService attachmentService;

    @Autowired
    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/initiate")
    @PreAuthorize("isAuthenticated()")
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        } catch (Exception e) {
            logger.error("初始化上传时发生未知错误 (用户: {}): {}", authentication != null ? authentication.getName() : "N/A", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @PostMapping("/upload-chunk")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> uploadChunk(
            @RequestHeader("X-Upload-Id") String uploadId,
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
            return ResponseEntity.ok("分块 " + (chunkNumber + 1) + "/" + totalChunks + " 上传成功。");

        } catch (IOException e) {
            logger.error("上传分块 {} 失败 (uploadId: {}, 用户: {}): {}", chunkNumber, uploadId, authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("分块上传失败: " + e.getMessage());
        } catch (Exception e) {
            logger.error("上传分块 {} 时发生错误 (uploadId: {}, 用户: {}): {}", chunkNumber, uploadId, authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/finalize/{uploadId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> finalizeUpload(
            @PathVariable String uploadId,
            @RequestParam("originalFileName") String originalFileName,
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
            Map<String, String> response = new HashMap<>();
            response.put("message", "文件上传并合并成功。");
            response.put("fileName", finalSavedFileName);
            response.put("uploadId", uploadId);
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            logger.error("完成上传 {} (原始文件名: {}, 用户: {}) 失败: {}", uploadId, originalFileName, authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "完成上传失败: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("完成上传 {} (原始文件名: {}, 用户: {}) 时发生错误: {}", uploadId, originalFileName, authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/download/{filename:.+}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable String filename, Authentication authentication) {
        try {
            Path filePath = attachmentService.getAttachment(filename);
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                if (authentication != null && authentication.getName() != null) {
                    logger.info("用户 '{}' 下载附件: {}", authentication.getName(), filename);
                } else {
                    logger.info("匿名用户尝试下载附件 (已通过认证检查，但principal名称为空): {}", filename);
                }

                String contentType = Files.probeContentType(filePath);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }

                return ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                logger.warn("请求下载的附件不存在或不可读: {}", filename);
                // Prefer throwing ResourceNotFoundException for GlobalExceptionHandler to handle
                throw new ResourceNotFoundException("无法读取文件: " + filename);
            }
        } catch (ResourceNotFoundException e) {
            logger.warn("下载附件 {} 失败，资源未找到: {}", filename, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } catch (Exception e) {
            logger.error("下载附件 {} 失败: {}", filename, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // 新增端点：删除已上传的附件
    @DeleteMapping("/file/{serverFileName:.+}")
    @PreAuthorize("isAuthenticated()") // 确保用户已认证，具体权限可在服务层进一步检查
    public ResponseEntity<Map<String, String>> deleteAttachment(
            @PathVariable String serverFileName,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
                logger.warn("未经授权或无法识别用户尝试删除附件: {}", serverFileName);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "用户未认证或无法识别。"));
            }
            String username = authentication.getName();

            attachmentService.deleteUploadedFile(serverFileName, username);

            Map<String, String> response = new HashMap<>();
            response.put("message", "附件 '" + serverFileName + "' 已成功删除。");
            return ResponseEntity.ok(response);

        } catch (ResourceNotFoundException e) {
            logger.warn("用户 '{}' 尝试删除不存在或无权访问的附件 '{}': {}", authentication.getName(), serverFileName, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (BusinessLogicException e) {
            logger.warn("用户 '{}' 删除附件 '{}' 时发生业务逻辑错误: {}", authentication.getName(), serverFileName, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            logger.error("用户 '{}' 删除附件 '{}' 时发生IO错误: {}",authentication.getName(), serverFileName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "删除附件时发生服务器内部错误。"));
        } catch (Exception e) {
            logger.error("用户 '{}' 删除附件 '{}' 时发生未知错误: {}", authentication.getName(), serverFileName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "删除附件时发生未知错误。"));
        }
    }
}