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

// import java.util.List; // 如果 List 未被使用，可以移除

@RestController
@RequestMapping("/api/system/users")
@PreAuthorize("hasRole('ROLE_ADMIN')") // 类级别权限，确保只有管理员能访问用户管理API
public class UserManagementController {

    private final SystemManagementService systemManagementService;

    @Autowired
    public UserManagementController(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    @PostMapping
    // 如果未来为管理员角色分配了名为 "新增用户" 的功能权限，可以使用:
    // @PreAuthorize("hasAuthority('新增用户')")
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
    // 如果未来为管理员角色分配了名为 "查看用户详情" 或 "查看用户列表" 的功能权限，可以使用:
    // @PreAuthorize("hasAuthority('查看用户详情') or hasAuthority('查看用户列表')")
    public ResponseEntity<User> getUserByUsername(@PathVariable String username) {
        User user = systemManagementService.findUserByUsername(username);
        return ResponseEntity.ok(user);
    }

    @GetMapping
    // 如果未来为管理员角色分配了名为 "查看用户列表" 的功能权限，可以使用:
    // @PreAuthorize("hasAuthority('查看用户列表')")
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
    // 如果未来为管理员角色分配了名为 "修改用户信息" 的功能权限，可以使用:
    // @PreAuthorize("hasAuthority('修改用户信息')")
    public ResponseEntity<User> updateUser(@PathVariable Long userId, @Valid @RequestBody UserUpdateRequest userUpdateRequest) {
        User userDetailsToUpdate = new User();
        userDetailsToUpdate.setEmail(userUpdateRequest.getEmail());
        userDetailsToUpdate.setEnabled(userUpdateRequest.isEnabled());
        userDetailsToUpdate.setRealName(userUpdateRequest.getRealName());

        User updatedUser = systemManagementService.updateUser(userId, userDetailsToUpdate);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/{userId}")
    // 如果未来为管理员角色分配了名为 "删除用户" 的功能权限，可以使用:
    // @PreAuthorize("hasAuthority('删除用户')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        systemManagementService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/assign-roles")
    // 如果未来为管理员角色分配了名为 "分配用户角色" 的功能权限，可以使用:
    // @PreAuthorize("hasAuthority('分配用户角色')")
    public ResponseEntity<User> assignRolesToUser(@PathVariable Long userId, @RequestBody AssignRolesRequest assignRolesRequest) {
        User updatedUser = systemManagementService.assignRolesToUser(userId, assignRolesRequest.getRoleNames());
        return ResponseEntity.ok(updatedUser);
    }
}