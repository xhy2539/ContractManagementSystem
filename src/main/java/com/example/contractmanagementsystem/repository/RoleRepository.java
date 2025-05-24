package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> { // Role 是实体类型, Integer 是主键类型

    // 根据角色名称查找角色
    Optional<Role> findByName(String name);
}