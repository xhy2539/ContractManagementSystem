package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.FileUploadProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileUploadProgressRepository extends JpaRepository<FileUploadProgress, String> {

    Optional<FileUploadProgress> findByUploadIdAndUploaderUsername(String uploadId, String uploaderUsername);

    // 用于清理过期或未完成的上传任务
    // 根据创建时间清理，适用于一直没有上传任何分块的记录
    List<FileUploadProgress> findByStatusAndCreatedAtBefore(String status, LocalDateTime olderThan);

    // 根据最后一个分块上传时间清理，适用于上传了一部分但未完成的记录
    List<FileUploadProgress> findByStatusAndLastChunkUploadedAtBefore(String status, LocalDateTime olderThan);
}