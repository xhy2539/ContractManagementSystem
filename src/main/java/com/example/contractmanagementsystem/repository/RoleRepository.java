package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Functionality;
import com.example.contractmanagementsystem.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List; // 引入 List
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByName(String name);

    // 用于检查功能是否被任何角色使用 (虽然在删除功能时我们是迭代角色，但这里可以备用)
    // long countByFunctionalitiesContains(Functionality functionality);
    List<Role> findAllByFunctionalitiesContains(Functionality functionality); // 新增方法
}