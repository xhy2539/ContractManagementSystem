package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.dto.attachment.FileUploadInitiateRequest;
import com.example.contractmanagementsystem.dto.attachment.FileUploadInitiateResponse;
import com.example.contractmanagementsystem.entity.FileUploadProgress;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Path;

public interface AttachmentService {

    /**
     * 初始化文件上传，返回一个唯一的上传ID。
     * @param request 包含文件名和大小的请求对象
     * @param username 当前操作的用户名
     * @return 包含上传ID和处理后文件名的响应对象
     * @throws IOException 如果创建临时目录或文件失败
     */
    FileUploadInitiateResponse initiateUpload(FileUploadInitiateRequest request, String username) throws IOException;

    /**
     * 上传文件分块。
     * @param uploadId 唯一的上传ID
     * @param chunkNumber 当前分块的序号 (从0开始)
     * @param totalChunks 总分块数
     * @param fileChunk 分块文件本身
     * @param username 当前操作的用户名
     * @throws IOException 如果保存分块失败
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果uploadId无效或未初始化
     * @throws com.example.contractmanagementsystem.exception.BusinessLogicException 如果分块信息不一致或上传已完成/取消
     */
    void uploadChunk(String uploadId, int chunkNumber, int totalChunks, MultipartFile fileChunk, String username) throws IOException;

    /**
     * 完成文件上传，合并所有分块。
     * @param uploadId 唯一的上传ID
     * @param originalFileName 原始文件名，用于最终保存
     * @param username 当前操作的用户名
     * @return 最终保存的文件名 (可能与原始文件名不同，例如添加了UUID)
     * @throws IOException 如果合并文件或移动文件失败
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果uploadId无效或某些分块丢失
     * @throws com.example.contractmanagementsystem.exception.BusinessLogicException 如果并非所有分块都已上传
     */
    String finalizeUpload(String uploadId, String originalFileName, String username) throws IOException;

    /**
     * 根据文件名获取附件的完整路径。
     * @param filename 附件的文件名
     * @return 附件的Path对象
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果文件未找到
     * @throws com.example.contractmanagementsystem.exception.BusinessLogicException 如果文件名无效
     */
    Path getAttachment(String filename);

    /**
     * 删除一个已上传（已合并完成）的附件文件。
     * 这将从最终存储目录中删除文件，并清理相关的 FileUploadProgress 记录和临时分块。
     *
     * @param serverFileName 要删除的附件在服务器上的文件名。
     * @param username       执行删除操作的用户名，用于权限验证和审计。
     * @throws IOException 如果删除文件或目录时发生 I/O 错误。
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果文件或上传记录未找到。
     * @throws com.example.contractmanagementsystem.exception.BusinessLogicException 如果因业务逻辑无法删除（例如，文件名无效）。
     */
    void deleteUploadedFile(String serverFileName, String username) throws IOException;

    /**
     * 获取指定上传ID的上传进度详情。
     * 用于断点续传时，客户端查询服务器端已确认的上传状态。
     *
     * @param uploadId 上传ID。
     * @param username 当前操作的用户名，用于权限验证。
     * @return FileUploadProgress 实体，包含了上传的详细信息。
     * @throws ResourceNotFoundException 如果指定uploadId的上传记录未找到。
     * @throws AccessDeniedException 如果当前用户无权查看此上传记录的状态。
     */
    FileUploadProgress getUploadProgressDetails(String uploadId, String username) throws ResourceNotFoundException, AccessDeniedException;

    /**
     * 清理指定的上传会话，删除相关的临时文件和数据库记录。
     * 用于处理异常情况或用户取消上传。
     *
     * @param uploadId 要清理的上传ID。
     * @param username 当前操作的用户名，用于权限验证。
     * @throws ResourceNotFoundException 如果指定uploadId的上传记录未找到。
     * @throws AccessDeniedException 如果当前用户无权操作此上传记录。
     * @throws IOException 如果清理临时文件时发生错误。
     */
    void cleanupUpload(String uploadId, String username) throws ResourceNotFoundException, AccessDeniedException, IOException;
}