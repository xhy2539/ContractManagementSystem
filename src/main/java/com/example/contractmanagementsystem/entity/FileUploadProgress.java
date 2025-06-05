package com.example.contractmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "file_upload_progress")
public class FileUploadProgress {

    @Id
    @Column(length = 36) // UUID 通常是36个字符
    private String uploadId; // 不自动生成，由程序在初始化时指定UUID

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false, unique = true) // 服务器端存储的最终文件名，应该是唯一的
    private String serverSideFileName;

    @Column(nullable = false)
    private Long totalSize;

    private Integer totalChunks; // 总块数，可能在第一个块上传时才知道

    @Column(nullable = false)
    private String uploaderUsername; // 上传者

    @Column(nullable = false)
    private String tempFileDirectory; // 存储该上传会话所有分块的临时目录路径 (相对于tempUploadPath)

    @Column(nullable = false)
    private String status; // 上传状态: IN_PROGRESS, COMPLETED, FAILED, CANCELLED

    @ElementCollection(fetch = FetchType.EAGER) // 急切加载已上传的块，因为通常会立即用到
    @CollectionTable(name = "file_upload_uploaded_chunks", joinColumns = @JoinColumn(name = "upload_id"))
    @Column(name = "chunk_number")
    private Set<Integer> uploadedChunks = new HashSet<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastChunkUploadedAt; // 记录最后一个块的上传时间，用于清理过期任务

    public FileUploadProgress(String uploadId, String originalFileName, String serverSideFileName, Long totalSize, String uploaderUsername, String tempFileDirectory) {
        this.uploadId = uploadId;
        this.originalFileName = originalFileName;
        this.serverSideFileName = serverSideFileName;
        this.totalSize = totalSize;
        this.uploaderUsername = uploaderUsername;
        this.tempFileDirectory = tempFileDirectory; // 例如 uploadId 本身作为目录名
        this.status = "IN_PROGRESS"; // 初始状态
        this.uploadedChunks = new HashSet<>();
    }

    public void addUploadedChunk(int chunkNumber) {
        this.uploadedChunks.add(chunkNumber);
        this.lastChunkUploadedAt = LocalDateTime.now();
    }

    public boolean areAllChunksUploaded() {
        if (this.totalChunks == null || this.totalChunks <= 0) {
            return false; // 如果总块数未知或无效，则认为未完成
        }
        return this.uploadedChunks.size() >= this.totalChunks;
    }
}