package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Functionality;
import org.springframework.data.domain.Page; // 新增导入
import org.springframework.data.domain.Pageable; // 新增导入
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor; // 新增导入
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface FunctionalityRepository extends JpaRepository<Functionality, Long>, JpaSpecificationExecutor<Functionality> { // 继承 JpaSpecificationExecutor

    Optional<Functionality> findByNum(String num);
    Optional<Functionality> findByName(String name);

    // 可选: 为简单的名称或编号搜索添加分页支持 (如果不用Specification)
    Page<Functionality> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Page<Functionality> findByNumContainingIgnoreCase(String num, Pageable pageable);
}