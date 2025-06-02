package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.dto.attachment.FileUploadInitiateRequest;
import com.example.contractmanagementsystem.dto.attachment.FileUploadInitiateResponse;
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
     * 查询上传状态，例如哪些分块已经上传。
     * (这个方法可以根据需要实现，对于简单的断点续传，客户端可以通过尝试上传来判断)
     * @param uploadId 唯一的上传ID
     * @return 返回已上传分块的信息或其他状态信息
     */
    // Object getUploadStatus(String uploadId); // 示例，具体返回类型待定

    /**
     * 根据文件名获取附件的完整路径。
     * (此方法可以从 ContractService 迁移或在此处重新实现，专门用于附件)
     * @param filename 附件的文件名
     * @return 附件的Path对象
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果文件未找到
     * @throws com.example.contractmanagementsystem.exception.BusinessLogicException 如果文件名无效
     */
    Path getAttachment(String filename);
}