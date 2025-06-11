package com.example.contractmanagementsystem.dto.attachment; // 或者您选择的子包

public class ChunkUploadResponse {

    private String uploadId;
    private int chunkNumber;
    private String message;
    private boolean completed; // 指示整个文件是否已上传完成

    public ChunkUploadResponse(String uploadId, int chunkNumber, String message, boolean completed) {
        this.uploadId = uploadId;
        this.chunkNumber = chunkNumber;
        this.message = message;
        this.completed = completed;
    }

    // Getters and Setters
    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public int getChunkNumber() {
        return chunkNumber;
    }

    public void setChunkNumber(int chunkNumber) {
        this.chunkNumber = chunkNumber;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}