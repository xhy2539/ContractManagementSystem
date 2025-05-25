package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Functionality;
import com.example.contractmanagementsystem.entity.Role;
import org.springframework.data.domain.Page; // 新增导入
import org.springframework.data.domain.Pageable; // 新增导入
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor; // 新增导入
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer>, JpaSpecificationExecutor<Role> { // 继承 JpaSpecificationExecutor

    Optional<Role> findByName(String name);
    List<Role> findAllByFunctionalitiesContains(Functionality functionality);

    // 可选: 为简单的名称搜索添加分页支持 (如果不用Specification)
    Page<Role> findByNameContainingIgnoreCase(String name, Pageable pageable);
}