package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Role;
import com.example.contractmanagementsystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List; // 引入 List
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> { // User 是实体类型, Long 是主键类型

    Optional<User> findByUsername(String username);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);

    // 用于检查角色是否被任何用户使用
    long countByRolesContains(Role role); // 新增方法，用于高效计数
    List<User> findAllByRolesContains(Role role); // 新增方法，获取所有包含指定角色的用户
}