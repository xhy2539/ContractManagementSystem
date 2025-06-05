package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.FileUploadProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileUploadProgressRepository extends JpaRepository<FileUploadProgress, String> { // 主键是 String (uploadId)

    Optional<FileUploadProgress> findByUploadIdAndUploaderUsername(String uploadId, String uploaderUsername);

    // 根据创建时间清理，适用于一直没有上传任何分块的记录
    List<FileUploadProgress> findByStatusAndCreatedAtBefore(String status, LocalDateTime olderThan);

    // 根据最后一个分块上传时间清理，适用于上传了一部分但未完成的记录
    List<FileUploadProgress> findByStatusAndLastChunkUploadedAtBefore(String status, LocalDateTime olderThan);

    // 新增方法：根据服务器端文件名和上传者用户名查找
    Optional<FileUploadProgress> findByServerSideFileNameAndUploaderUsername(String serverSideFileName, String uploaderUsername);

    // 新增方法：根据服务器端文件名查找
    Optional<FileUploadProgress> findByServerSideFileName(String serverSideFileName);
}