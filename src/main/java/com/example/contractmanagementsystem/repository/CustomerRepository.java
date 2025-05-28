package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // 建议客户编号查询也忽略大小写，以提高容错性
    Optional<Customer> findByCustomerNumberIgnoreCase(String customerNumber);

    Page<Customer> findByCustomerNameContainingIgnoreCase(String customerName, Pageable pageable);
    Optional<Customer> findByCustomerName(String customerName); // 可以保留，但主要用下面的
    Optional<Customer> findByEmail(String email);
    List<Customer> findAllByCustomerNameIgnoreCase(String customerName);

    // --- 关键方法：检查名称是否被其他客户编号占用 ---
    /**
     * 检查是否存在一个客户，其名称与给定名称匹配（忽略大小写），
     * 但其客户编号与给定的排除编号不匹配（忽略大小写）。
     * @param customerName 要检查的客户名称
     * @param customerNumberToExclude 要排除的客户编号
     * @return 如果存在这样的客户，则返回true；否则返回false。
     */
    boolean existsByCustomerNameIgnoreCaseAndCustomerNumberNotIgnoreCase(String customerName, String customerNumberToExclude);

    // 保留原有的findByCustomerNumber，如果业务上严格区分大小写
    Optional<Customer> findByCustomerNumber(String customerNumber);

}