package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.AssignRolesRequest;
import com.example.contractmanagementsystem.dto.UserCreationRequest;
import com.example.contractmanagementsystem.dto.UserUpdateRequest;
import com.example.contractmanagementsystem.entity.User;
import com.example.contractmanagementsystem.service.SystemManagementService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
        User newUser = new User();
        newUser.setUsername(userRequest.getUsername());
        newUser.setPassword(userRequest.getPassword());
        newUser.setEmail(userRequest.getEmail());
        User createdUser = systemManagementService.createUser(newUser, userRequest.getRoleNames());
        return new ResponseEntity<>(createdUser, HttpStatus.CREATED);
    }

    @GetMapping("/{username}")
    public ResponseEntity<User> getUserByUsername(@PathVariable String username) {
        User user = systemManagementService.findUserByUsername(username);
        return ResponseEntity.ok(user);
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = systemManagementService.getAllUsers();
        return ResponseEntity.ok(users);
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