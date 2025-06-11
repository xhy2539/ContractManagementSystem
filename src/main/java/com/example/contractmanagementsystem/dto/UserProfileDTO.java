package com.example.contractmanagementsystem.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class UserProfileDTO {
    
    @Email(message = "请输入有效的邮箱地址")
    private String email;
} 