package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Role;
import com.example.contractmanagementsystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query; // 新增导入
import org.springframework.data.repository.query.Param; // 新增导入
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);
    Boolean existsByUsername(String username);

    Optional<User> findByEmail(String email);
    Boolean existsByEmail(String email);

    long countByRolesContains(Role role);
    List<User> findAllByRolesContains(Role role);

    // 新增方法：预先加载所有用户及其角色和功能
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.functionalities")
    List<User> findAllWithRolesAndFunctionalities();

    // 新增方法：根据用户名查找用户并预先加载其角色和功能
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.functionalities WHERE u.username = :username")
    Optional<User> findByUsernameWithRolesAndFunctionalities(@Param("username") String username);
}