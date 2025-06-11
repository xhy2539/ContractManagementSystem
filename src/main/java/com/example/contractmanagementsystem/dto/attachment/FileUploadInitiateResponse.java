package com.example.contractmanagementsystem.dto.attachment; // 或者您选择的子包

public class FileUploadInitiateResponse {

    private String uploadId;
    private String fileName; // 可以是服务器处理后的文件名

    public FileUploadInitiateResponse(String uploadId, String fileName) {
        this.uploadId = uploadId;
        this.fileName = fileName;
    }

    // Getters and Setters
    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}