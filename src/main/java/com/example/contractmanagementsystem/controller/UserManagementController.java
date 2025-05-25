package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.AssignRolesRequest;
import com.example.contractmanagementsystem.dto.UserCreationRequest;
import com.example.contractmanagementsystem.dto.UserUpdateRequest;
import com.example.contractmanagementsystem.entity.User;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.service.SystemManagementService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/users")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class UserManagementController {

    private final SystemManagementService systemManagementService;

    @Autowired
    public UserManagementController(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    @PostMapping
    public ResponseEntity<User> createUser(@Valid @RequestBody UserCreationRequest userRequest) {
        if (!userRequest.getPassword().equals(userRequest.getConfirmPassword())) {
            throw new BusinessLogicException("两次输入的密码不一致。");
        }

        User newUser = new User();
        newUser.setUsername(userRequest.getUsername());
        newUser.setPassword(userRequest.getPassword());
        newUser.setEmail(userRequest.getEmail());
        newUser.setRealName(userRequest.getRealName()); // 设置 realName

        User createdUser = systemManagementService.createUser(newUser, userRequest.getRoleNames());
        return new ResponseEntity<>(createdUser, HttpStatus.CREATED);
    }

    @GetMapping("/{username}")
    public ResponseEntity<User> getUserByUsername(@PathVariable String username) {
        User user = systemManagementService.findUserByUsername(username);
        return ResponseEntity.ok(user);
    }

    @GetMapping
    public ResponseEntity<Page<User>> getAllUsers(
            @PageableDefault(size = 10, sort = "username", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String usernameSearch,
            @RequestParam(required = false) String emailSearch) {

        Page<User> usersPage;
        if ((usernameSearch != null && !usernameSearch.isEmpty()) || (emailSearch != null && !emailSearch.isEmpty())) {
            usersPage = systemManagementService.searchUsers(usernameSearch, emailSearch, pageable);
        } else {
            usersPage = systemManagementService.getAllUsers(pageable);
        }
        return ResponseEntity.ok(usersPage);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<User> updateUser(@PathVariable Long userId, @Valid @RequestBody UserUpdateRequest userUpdateRequest) {
        User userDetailsToUpdate = new User(); // 创建一个临时的 User 对象来传递更新信息
        userDetailsToUpdate.setEmail(userUpdateRequest.getEmail());
        userDetailsToUpdate.setEnabled(userUpdateRequest.isEnabled());
        userDetailsToUpdate.setRealName(userUpdateRequest.getRealName()); // 设置 realName

        User updatedUser = systemManagementService.updateUser(userId, userDetailsToUpdate);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        systemManagementService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/assign-roles")
    public ResponseEntity<User> assignRolesToUser(@PathVariable Long userId, @RequestBody AssignRolesRequest assignRolesRequest) {
        User updatedUser = systemManagementService.assignRolesToUser(userId, assignRolesRequest.getRoleNames());
        return ResponseEntity.ok(updatedUser);
    }
}