package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.attachment.FileUploadInitiateRequest;
import com.example.contractmanagementsystem.dto.attachment.FileUploadInitiateResponse;
import com.example.contractmanagementsystem.entity.FileUploadProgress; // 新增导入
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.service.AttachmentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException; // 新增导入
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
                logger.warn("未认证用户尝试上传分块 (uploadId: {})", uploadId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户未认证");
            }
            String username = authentication.getName();
            if (username == null) {
                logger.error("无法获取认证用户名 (uploadId: {})", uploadId);
                throw new UsernameNotFoundException("无法获取当前认证的用户名。");
            }

            if (fileChunk.isEmpty()) {
                logger.warn("用户 '{}' 上传空分块 (uploadId: {}, 分块: {})", username, uploadId, chunkNumber);
                return ResponseEntity.badRequest().body("上传的分块不能为空");
            }

            logger.info("用户 '{}' 开始上传分块: uploadId={}, 分块={}/{}, 大小={} bytes", 
                        username, uploadId, chunkNumber + 1, totalChunks, fileChunk.getSize());

            attachmentService.uploadChunk(uploadId, chunkNumber, totalChunks, fileChunk, username);
            
            logger.info("用户 '{}' 分块上传成功: uploadId={}, 分块={}/{}", 
                        username, uploadId, chunkNumber + 1, totalChunks);
            
            return ResponseEntity.ok("分块 " + (chunkNumber + 1) + "/" + totalChunks + " 上传成功。");

        } catch (IOException e) {
            logger.error("用户 '{}' 上传分块 {} 失败 (uploadId: {}): {}", 
                        authentication != null ? authentication.getName() : "未知", chunkNumber, uploadId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("分块上传失败: " + e.getMessage());
        } catch (Exception e) {
            logger.error("用户 '{}' 上传分块 {} 时发生错误 (uploadId: {}): {}", 
                        authentication != null ? authentication.getName() : "未知", chunkNumber, uploadId, e.getMessage(), e);
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

    @DeleteMapping("/cleanup/{uploadId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> cleanupUpload(
            @PathVariable String uploadId,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            String username = authentication.getName();
            if (username == null) {
                throw new UsernameNotFoundException("无法获取当前认证的用户名。");
            }

            attachmentService.cleanupUpload(uploadId, username);
            Map<String, String> response = new HashMap<>();
            response.put("message", "上传会话已清理。");
            response.put("uploadId", uploadId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("清理上传会话 {} (用户: {}) 时发生错误: {}", uploadId, authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "清理失败: " + e.getMessage()));
        }
    }

    @GetMapping("/download/{filename:.+}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable String filename, Authentication authentication) {
        try {
            Path filePath = attachmentService.getAttachment(filename);
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                logger.warn("请求下载的附件不存在或不可读: {}", filename);
                throw new ResourceNotFoundException("无法读取文件: " + filename);
            }

            if (authentication != null && authentication.getName() != null) {
                logger.info("用户 '{}' 正在访问附件: {}", authentication.getName(), filename);
            }

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream"; // 默认类型
            }

            // --- 核心修改：根据文件类型决定 Content-Disposition ---
            String dispositionType;
            String lowerCaseFilename = filename.toLowerCase();

            // 定义可以在浏览器中直接预览的文件扩展名
            if (lowerCaseFilename.endsWith(".pdf") ||
                    // 图片文件
                    lowerCaseFilename.endsWith(".jpg") ||
                    lowerCaseFilename.endsWith(".jpeg") ||
                    lowerCaseFilename.endsWith(".png") ||
                    lowerCaseFilename.endsWith(".gif") ||
                    lowerCaseFilename.endsWith(".bmp") ||
                    lowerCaseFilename.endsWith(".webp") ||
                    lowerCaseFilename.endsWith(".svg") ||
                    // 文本文件
                    lowerCaseFilename.endsWith(".txt") ||
                    lowerCaseFilename.endsWith(".json") ||
                    lowerCaseFilename.endsWith(".xml") ||
                    lowerCaseFilename.endsWith(".csv") ||
                    // 多媒体文件
                    lowerCaseFilename.endsWith(".mp4") ||
                    lowerCaseFilename.endsWith(".mp3") ||
                    lowerCaseFilename.endsWith(".wav")) {
                dispositionType = "inline"; // "inline" 表示建议浏览器内联显示（预览）
            } else {
                dispositionType = "attachment"; // "attachment" 表示强制下载
            }
            // --- 修改结束 ---

            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                    // 使用动态决定的 dispositionType
                    .header(HttpHeaders.CONTENT_DISPOSITION, dispositionType + "; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (ResourceNotFoundException e) {
            logger.warn("下载附件 {} 失败，资源未找到: {}", filename, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } catch (Exception e) {
            logger.error("下载附件 {} 失败: {}", filename, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/file/{serverFileName:.+}")
    @PreAuthorize("isAuthenticated()")
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

    // 新增端点：获取上传状态
    @GetMapping("/status/{uploadId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getUploadStatus(@PathVariable String uploadId, Authentication authentication) {
        try {
            String username = authentication.getName();
            if (username == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "无法识别用户。"));
            }

            FileUploadProgress progress = attachmentService.getUploadProgressDetails(uploadId, username);

            // 构建响应体
            Map<String, Object> statusResponse = new HashMap<>();
            statusResponse.put("uploadId", progress.getUploadId());
            statusResponse.put("serverFileName", progress.getServerSideFileName());
            statusResponse.put("originalFileName", progress.getOriginalFileName());
            statusResponse.put("status", progress.getStatus());
            statusResponse.put("uploadedChunks", progress.getUploadedChunks()); // Set<Integer>
            statusResponse.put("totalChunks", progress.getTotalChunks());
            statusResponse.put("totalSize", progress.getTotalSize());

            return ResponseEntity.ok(statusResponse);

        } catch (ResourceNotFoundException e) {
            logger.warn("用户 '{}' 查询上传状态失败，UploadId '{}' 未找到: {}", authentication.getName(), uploadId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (AccessDeniedException e) {
            logger.warn("用户 '{}' 无权访问 UploadId '{}' 的上传状态: {}", authentication.getName(), uploadId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("用户 '{}' 获取上传ID '{}' 的状态时发生错误: {}", authentication.getName(), uploadId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取上传状态时发生服务器内部错误。"));
        }
    }
}