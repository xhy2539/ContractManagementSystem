package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.dto.UserProfileDTO;

public interface UserService {
    UserProfileDTO getUserProfile(String username);
    void updateUserProfile(String username, UserProfileDTO userProfile);
    void changePassword(String username, String currentPassword, String newPassword, String confirmPassword);
} 