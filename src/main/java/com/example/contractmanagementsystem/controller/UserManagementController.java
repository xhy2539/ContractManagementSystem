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
    @PreAuthorize("hasAuthority('USER_CREATE')") // 使用功能编号
    public ResponseEntity<User> createUser(@Valid @RequestBody UserCreationRequest userRequest) {
        if (!userRequest.getPassword().equals(userRequest.getConfirmPassword())) {
            throw new BusinessLogicException("两次输入的密码不一致。");
        }

        User newUser = new User();
        newUser.setUsername(userRequest.getUsername());
        newUser.setPassword(userRequest.getPassword());
        newUser.setEmail(userRequest.getEmail());
        newUser.setRealName(userRequest.getRealName());

        User createdUser = systemManagementService.createUser(newUser, userRequest.getRoleNames());
        return new ResponseEntity<>(createdUser, HttpStatus.CREATED);
    }

    @GetMapping("/{username}")
    @PreAuthorize("hasAuthority('USER_VIEW_LIST')") // 使用功能编号 (假设查看详情也用此权限)
    public ResponseEntity<User> getUserByUsername(@PathVariable String username) {
        User user = systemManagementService.findUserByUsername(username);
        return ResponseEntity.ok(user);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW_LIST')") // 使用功能编号
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
    @PreAuthorize("hasAuthority('USER_EDIT')") // 使用功能编号
    public ResponseEntity<User> updateUser(@PathVariable Long userId, @Valid @RequestBody UserUpdateRequest userUpdateRequest) {
        User userDetailsToUpdate = new User();
        userDetailsToUpdate.setEmail(userUpdateRequest.getEmail());
        userDetailsToUpdate.setEnabled(userUpdateRequest.isEnabled());
        userDetailsToUpdate.setRealName(userUpdateRequest.getRealName());

        User updatedUser = systemManagementService.updateUser(userId, userDetailsToUpdate);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('USER_DELETE')") // 使用功能编号
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        systemManagementService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/assign-roles")
    @PreAuthorize("hasAuthority('USER_ASSIGN_ROLES')") // 使用功能编号 (这个之前已确认)
    public ResponseEntity<User> assignRolesToUser(@PathVariable Long userId, @RequestBody AssignRolesRequest assignRolesRequest) {
        User updatedUser = systemManagementService.assignRolesToUser(userId, assignRolesRequest.getRoleNames());
        return ResponseEntity.ok(updatedUser);
    }
}