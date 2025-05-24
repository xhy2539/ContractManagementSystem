package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // 根据客户编号查找客户
    Optional<Customer> findByCustomerNumber(String customerNumber);

    // 根据客户名称模糊查询 (分页)
    Page<Customer> findByCustomerNameContainingIgnoreCase(String customerName, Pageable pageable);

    // 根据邮箱查找客户
    Optional<Customer> findByEmail(String email);
}