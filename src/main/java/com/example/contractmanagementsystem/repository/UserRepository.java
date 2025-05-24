package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> { // User 是实体类型, Long 是主键类型

    // 根据用户名查找用户 (Spring Data JPA 会自动实现)
    Optional<User> findByUsername(String username);

    // 检查用户名是否存在
    Boolean existsByUsername(String username);

    // 检查邮箱是否存在
    Boolean existsByEmail(String email);
}