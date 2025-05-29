package com.example.contractmanagementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    
    @NotBlank(message = "当前密码不能为空")
    private String currentPassword;
    
    @NotBlank(message = "新密码不能为空")
    private String newPassword;
    
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
} 