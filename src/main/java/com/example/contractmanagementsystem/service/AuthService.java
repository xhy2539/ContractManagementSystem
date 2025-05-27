package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.dto.auth.LoginRequest;
import com.example.contractmanagementsystem.dto.auth.RegistrationRequest;
import com.example.contractmanagementsystem.dto.auth.AuthResponse;
import com.example.contractmanagementsystem.entity.User;

public interface AuthService {

    /**
     * 用户注册功能。
     *
     * @param request 包含注册信息的DTO
     * @return 注册成功的用户实体
     */
    AuthResponse registerUser(RegistrationRequest request);

    /**
     * 用户登录功能。
     *
     * @param request 包含登录凭证的DTO
     * @return 认证响应，可能包含JWT token等信息
     */
    AuthResponse authenticateUser(LoginRequest request);

    // 可以根据需要添加其他与认证相关的服务方法
}