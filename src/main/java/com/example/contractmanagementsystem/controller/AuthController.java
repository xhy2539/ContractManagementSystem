package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.auth.LoginRequest;
import com.example.contractmanagementsystem.dto.auth.RegistrationRequest;
import com.example.contractmanagementsystem.dto.auth.AuthResponse;
import com.example.contractmanagementsystem.entity.User; // 如果注册成功后返回User实体

import com.example.contractmanagementsystem.service.AuthService; // 导入AuthService接口

import jakarta.validation.Valid; // 用于DTO校验

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth") // 定义所有认证相关接口的基础路径
public class AuthController {

    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 处理用户注册请求。
     * POST /api/auth/register
     *
     * @param request 注册请求DTO
     * @return 注册成功的用户信息或错误信息
     */
    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@Valid @RequestBody RegistrationRequest request) {
        User registeredUser = authService.registerUser(request);
        // 通常注册成功后会返回用户ID，或者部分信息，避免返回密码等敏感信息
        // 这里的User对象中，密码已经被PasswordEncoder处理过，不会是明文
        return new ResponseEntity<>(registeredUser, HttpStatus.CREATED);
    }

    /**
     * 处理用户登录请求。
     * POST /api/auth/login
     *
     * @param request 登录请求DTO
     * @return 认证响应，包含JWT token等信息
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticateUser(@Valid @RequestBody LoginRequest request) {
        // 调用AuthService的登录方法
        AuthResponse authResponse = authService.authenticateUser(request);
        return ResponseEntity.ok(authResponse); // 登录成功返回200 OK
    }
}