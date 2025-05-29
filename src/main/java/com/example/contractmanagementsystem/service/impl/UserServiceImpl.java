package com.example.contractmanagementsystem.service.impl;

import com.example.contractmanagementsystem.dto.UserProfileDTO;
import com.example.contractmanagementsystem.entity.User;
import com.example.contractmanagementsystem.repository.UserRepository;
import com.example.contractmanagementsystem.service.UserService;
import com.example.contractmanagementsystem.exception.DuplicateResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserProfileDTO getUserProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        
        UserProfileDTO profileDTO = new UserProfileDTO();
        profileDTO.setEmail(user.getEmail());
        return profileDTO;
    }

    @Override
    @Transactional
    public void updateUserProfile(String username, UserProfileDTO userProfile) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        
        // 检查邮箱是否已被其他用户使用
        if (userProfile.getEmail() != null && !userProfile.getEmail().isEmpty() &&
                !userProfile.getEmail().equals(user.getEmail())) {
            userRepository.findByEmail(userProfile.getEmail())
                    .filter(u -> !u.getId().equals(user.getId()))
                    .ifPresent(u -> {
                        throw new DuplicateResourceException("邮箱 '" + userProfile.getEmail() + "' 已被其他用户使用");
                    });
            user.setEmail(userProfile.getEmail());
        }
        
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword, String confirmPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));

        // 验证当前密码是否正确
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("当前密码不正确");
        }

        // 验证新密码和确认密码是否一致
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("新密码和确认密码不一致");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
} 