package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor; // 新增：确保导入此接口
import org.springframework.stereotype.Repository;
import java.util.List; // 如果 findAllByCustomerNameIgnoreCase 仍被使用
import java.util.Optional;

@Repository
// 修改点：添加 JpaSpecificationExecutor<Customer> 的继承
public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    // 建议客户编号查询也忽略大小写，以提高容错性
    Optional<Customer> findByCustomerNumberIgnoreCase(String customerNumber);

    // 此方法可以保留，如果 CustomerService 中 searchCustomers 方法不使用 Specification 时会用到
    // 或者如果其他地方直接调用了这个方法
    Page<Customer> findByCustomerNameContainingIgnoreCase(String customerName, Pageable pageable);

    // 如果业务上需要根据客户名称精确匹配（忽略大小写）且不分页的场景，可以保留
    Optional<Customer> findByCustomerName(String customerName); // 注意：这个通常用于精确匹配，忽略大小写版本可能更有用

    // 建议也改成 findByEmailIgnoreCase，或者在数据库层面设置邮箱字段为不区分大小写的唯一索引
    Optional<Customer> findByEmail(String email);

    // 如果业务上需要获取所有名称匹配（忽略大小写）的客户列表，可以保留
    List<Customer> findAllByCustomerNameIgnoreCase(String customerName); // 确保 List 被导入

    // --- 关键方法：检查名称是否被其他客户编号占用 ---
    /**
     * 检查是否存在一个客户，其名称与给定名称匹配（忽略大小写），
     * 但其客户编号与给定的排除编号不匹配（忽略大小写）。
     * 这个方法在 CustomerService 的 addCustomer 和 updateCustomer 逻辑中可能很有用，
     * 用来防止不同编号的客户使用相同的名称。
     * @param customerName 要检查的客户名称
     * @param customerNumberToExclude 要排除的客户编号 (当前正在创建或更新的客户的编号)
     * @return 如果存在这样的客户，则返回true；否则返回false。
     */
    boolean existsByCustomerNameIgnoreCaseAndCustomerNumberNotIgnoreCase(String customerName, String customerNumberToExclude);

    // 保留原有的findByCustomerNumber，如果业务上严格区分大小写
    Optional<Customer> findByCustomerNumber(String customerNumber);

}