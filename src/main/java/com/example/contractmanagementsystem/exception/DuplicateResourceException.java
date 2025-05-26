package com.example.contractmanagementsystem.exception;

import org.springframework.http.HttpStatus; // 确保导入此行
import org.springframework.web.bind.annotation.ResponseStatus; // 确保导入此行

@ResponseStatus(HttpStatus.CONFLICT) // HTTP 409 Conflict 状态码
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}