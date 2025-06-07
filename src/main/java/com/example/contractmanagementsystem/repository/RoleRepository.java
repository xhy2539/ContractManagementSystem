package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Functionality;
import com.example.contractmanagementsystem.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query; // 新增导入
import org.springframework.data.repository.query.Param; // 新增导入
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer>, JpaSpecificationExecutor<Role> {

    Optional<Role> findByName(String name);
    List<Role> findAllByFunctionalitiesContains(Functionality functionality);

    Page<Role> findByNameContainingIgnoreCase(String name, Pageable pageable);

    // 新增方法：预先加载所有角色及其功能
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.functionalities")
    List<Role> findAllWithFunctionalities();

    // 新增方法：根据名称查找角色并预先加载其功能
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.functionalities WHERE r.name = :name")
    Optional<Role> findByNameWithFunctionalities(@Param("name") String name);

    // 新增方法：根据ID查找角色并预先加载其功能
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.functionalities WHERE r.id = :id")
    Optional<Role> findByIdWithFunctionalities(@Param("id") Integer id);
}