package com.example.contractmanagementsystem.dto.attachment; // 或者您选择的子包

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

public class FileUploadInitiateRequest {

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @NotNull(message = "文件总大小不能为空")
    @Min(value = 1, message = "文件大小必须大于0")
    private Long totalSize;

    // 文件类型 (例如 "application/pdf")，可选，但推荐
    private String contentType;

    // Getters and Setters
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(Long totalSize) {
        this.totalSize = totalSize;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}