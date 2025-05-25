package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.AssignRolesRequest;
import com.example.contractmanagementsystem.dto.UserCreationRequest;
import com.example.contractmanagementsystem.dto.UserUpdateRequest;
import com.example.contractmanagementsystem.entity.User;
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

import java.util.List; // 保留 List 的导入，如果其他地方还需要

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
        User newUser = new User();
        newUser.setUsername(userRequest.getUsername());
        newUser.setPassword(userRequest.getPassword());
        newUser.setEmail(userRequest.getEmail());
        // 角色在 UserCreationRequest 中通过 roleNames 传递，Service层会处理
        User createdUser = systemManagementService.createUser(newUser, userRequest.getRoleNames());
        return new ResponseEntity<>(createdUser, HttpStatus.CREATED);
    }

    @GetMapping("/{username}")
    public ResponseEntity<User> getUserByUsername(@PathVariable String username) {
        User user = systemManagementService.findUserByUsername(username);
        return ResponseEntity.ok(user);
    }

    /**
     * 获取所有用户列表（支持分页和搜索）
     * @param pageable 分页和排序参数 (例如 ?page=0&size=10&sort=username,asc)
     * @param usernameSearch 可选的用户名搜索关键词
     * @param emailSearch 可选的邮箱搜索关键词
     * @return 用户分页数据
     */
    @GetMapping
    public ResponseEntity<Page<User>> getAllUsers(
            @PageableDefault(size = 10, sort = "username", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String usernameSearch,
            @RequestParam(required = false) String emailSearch) {

        // 注意: 下面的 systemManagementService.searchUsers(...) 方法需要在 Service 和 Repository 层实现
        // 这里我们先假设一个 getAllUsers(pageable) 的分页方法已经或即将被实现
        // 为了简化，我们暂时只使用无搜索的分页，如果需要搜索，Service层需要更复杂的逻辑
        // 实际中，你可能会有一个更通用的搜索方法，或者根据传入的参数构建动态查询

        Page<User> usersPage;
        if ((usernameSearch != null && !usernameSearch.isEmpty()) || (emailSearch != null && !emailSearch.isEmpty())) {
            // 假设有一个搜索方法，这里只是一个占位符，具体实现依赖于Service层
            usersPage = systemManagementService.searchUsers(usernameSearch, emailSearch, pageable);
        } else {
            usersPage = systemManagementService.getAllUsers(pageable); // 调用支持分页的Service方法
        }
        return ResponseEntity.ok(usersPage);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<User> updateUser(@PathVariable Long userId, @Valid @RequestBody UserUpdateRequest userUpdateRequest) {
        User userDetailsToUpdate = new User();
        userDetailsToUpdate.setEmail(userUpdateRequest.getEmail());
        userDetailsToUpdate.setEnabled(userUpdateRequest.isEnabled()); // Getter isEnabled()
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