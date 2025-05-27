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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Autowired
    public AuthServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    @Override
    @Transactional
    public AuthResponse registerUser(RegistrationRequest request) {
        // 1. 检查用户名和邮箱是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("用户名 '" + request.getUsername() + "' 已被注册。");
        }
        if (request.getEmail() != null && !request.getEmail().isEmpty() && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("邮箱 '" + request.getEmail() + "' 已被注册。");
        }

        // 2. 创建User实体
        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setEmail(request.getEmail());
        newUser.setEnabled(true);

        // 3. 根据请求的 roleType 分配角色
        String requestedRoleType = request.getRoleType().toUpperCase(); // 转换为大写，方便比较
        String roleNameToAdd;

        if ("USER".equals(requestedRoleType)) {
            roleNameToAdd = "ROLE_USER";
        } else if ("CONTRACT_OPERATOR".equals(requestedRoleType)) {
            roleNameToAdd = "ROLE_CONTRACT_OPERATOR";
        } else {
            // 如果请求的角色类型不合法，或者尝试注册管理员角色，则抛出异常
            throw new IllegalArgumentException("无效的角色类型或无权注册此角色: " + request.getRoleType());
        }

        Optional<Role> selectedRoleOptional = roleRepository.findByName(roleNameToAdd);
        Role selectedRole = selectedRoleOptional.orElseThrow(() ->
                new ResourceNotFoundException("所选角色 '" + roleNameToAdd + "' 未找到，请联系管理员配置。")
        );

        newUser.getRoles().add(selectedRole);

        // 4. 保存用户
        User savedUser = userRepository.save(newUser);

        // 5. 构建并返回 AuthResponse DTO
        AuthResponse response = new AuthResponse();
        response.setToken("Registration successful for " + roleNameToAdd); // 可以在token中提示角色
        response.setUsername(savedUser.getUsername());
        response.setUserId(savedUser.getId());

        return response;
    }

    @Override
    @Transactional
    public AuthResponse authenticateUser(LoginRequest request) {
        // ... (这部分代码保持不变)
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String username = authentication.getName();
        User authenticatedUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("认证成功但未找到用户实体，请检查数据完整性。"));

        AuthResponse authResponse = new AuthResponse();
        authResponse.setToken("Authentication successful via Session");
        authResponse.setUsername(authenticatedUser.getUsername());
        authResponse.setUserId(authenticatedUser.getId());

        return authResponse;
    }
}