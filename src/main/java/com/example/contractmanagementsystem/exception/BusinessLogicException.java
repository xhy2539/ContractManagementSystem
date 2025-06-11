package com.example.contractmanagementsystem.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST) // 或者 HttpStatus.CONFLICT (409) 更适合业务逻辑冲突
public class BusinessLogicException extends RuntimeException {
    public BusinessLogicException(String message) {
        super(message);
    }

    public BusinessLogicException(String message, Throwable cause) {
        super(message, cause);
    }
}