package com.example.contractmanagementsystem.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token; // 通常是JWT token
    private String username;
    private Long userId; // 用户ID
    // private List<String> roles; // 如果需要返回用户角色
    // 可以添加其他需要返回给前端的用户信息，例如：
    // private String email;
    // private String realName;
}