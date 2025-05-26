package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.dto.auth.LoginRequest;
import com.example.contractmanagementsystem.dto.auth.RegistrationRequest;
import com.example.contractmanagementsystem.dto.auth.AuthResponse;
import com.example.contractmanagementsystem.entity.User;
import com.example.contractmanagementsystem.entity.Role;
import com.example.contractmanagementsystem.repository.UserRepository;
import com.example.contractmanagementsystem.repository.RoleRepository;

import com.example.contractmanagementsystem.exception.DuplicateResourceException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager; // 导入 AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // 导入 UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication; // 导入 Authentication
import org.springframework.security.core.context.SecurityContextHolder; // 导入 SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;

@Service
public class AuthServiceImpl implements AuthService { // 实现AuthService接口

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager; // 注入 AuthenticationManager

    // 构造器注入所有依赖
    @Autowired
    public AuthServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager) { // 新增 AuthenticationManager 注入
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager; // 初始化
    }

    @Override // 实现接口方法
    @Transactional
    public User registerUser(RegistrationRequest request) {
        // 1. 检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("用户名 '" + request.getUsername() + "' 已被注册。");
        }
        // 2. 检查邮箱是否已存在
        if (request.getEmail() != null && !request.getEmail().isEmpty() && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("邮箱 '" + request.getEmail() + "' 已被注册。");
        }

        // 3. 创建User实体
        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setPassword(passwordEncoder.encode(request.getPassword())); // 密码加密
        newUser.setEmail(request.getEmail());
        newUser.setEnabled(true); // 默认启用

        // 4. 分配默认角色
        // 这里需要你提前在数据库中有一个名为 "ROLE_USER" 的角色，或者根据你的实际需求调整
        Optional<Role> userRoleOptional = roleRepository.findByName("ROLE_USER");
        Role userRole = userRoleOptional.orElseGet(() -> {
            // 如果默认角色不存在，可以创建一个并保存，或者抛出异常
            // 这里我们选择抛出异常，因为通常默认角色应提前配置好
            throw new ResourceNotFoundException("默认角色 'ROLE_USER' 未找到，请联系管理员配置。");
        });

        newUser.getRoles().add(userRole);

        // 5. 保存用户
        return userRepository.save(newUser);
    }

    @Override // 实现接口方法
    @Transactional // 登录操作也可能需要事务
    public AuthResponse authenticateUser(LoginRequest request) {
        // 使用 AuthenticationManager 进行认证
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        // 将认证信息存储到 SecurityContextHolder
        // 这对于后续的请求（通过会话）保持认证状态非常重要
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 在Session/Cookie认证模式下，通常不需要返回一个“token”字段给前端，
        // 因为认证状态是通过JSESSIONID Cookie维护的。
        // 但是 AuthResponse DTO 中定义了 token 字段，我们可以根据需要来处理。
        // 在这里，token 字段可以填充一个表示认证成功但不具体是JWT的字符串。
        // 用户名和用户ID是常用的返回信息。
        String username = authentication.getName();
        User authenticatedUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("认证成功但未找到用户实体，请检查数据完整性。"));

        AuthResponse authResponse = new AuthResponse();
        // 在Session/Cookie模式下，这个token字段通常是空的或仅作标识
        authResponse.setToken("Authentication successful via Session"); // 示例值
        authResponse.setUsername(authenticatedUser.getUsername());
        authResponse.setUserId(authenticatedUser.getId());
        // 如果需要返回角色信息，可以在AuthResponse中添加List<String> roles;
        // authResponse.setRoles(authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()));

        return authResponse;
    }
}