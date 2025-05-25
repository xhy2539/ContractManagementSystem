package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Role;
import com.example.contractmanagementsystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor; // 确保这个导入存在，如果之前已添加
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
// 如果您在SystemManagementServiceImpl中使用了Specification进行用户搜索，请确保继承JpaSpecificationExecutor
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);
    Boolean existsByUsername(String username);

    Optional<User> findByEmail(String email); // <--- 新增此方法声明
    Boolean existsByEmail(String email); // 这个方法已经存在，用于快速检查

    long countByRolesContains(Role role);
    List<User> findAllByRolesContains(Role role);
}