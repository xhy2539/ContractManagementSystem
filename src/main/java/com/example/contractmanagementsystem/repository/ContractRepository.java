package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.Customer;
import com.example.contractmanagementsystem.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor; // 用于动态条件查询
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
// JpaSpecificationExecutor 允许你使用 JPA Criteria API 进行更复杂的动态查询
public interface ContractRepository extends JpaRepository<Contract, Long>, JpaSpecificationExecutor<Contract> {

    // 根据合同编号查找合同
    Optional<Contract> findByContractNumber(String contractNumber);

    // 根据合同名称模糊查询 (分页)
    Page<Contract> findByContractNameContainingIgnoreCase(String contractName, Pageable pageable);

    // 根据客户查找合同列表
    List<Contract> findByCustomer(Customer customer);

    // 根据起草人查找合同列表
    List<Contract> findByDrafter(User drafter);

    // 根据状态查找合同列表 (分页)
    Page<Contract> findByStatus(String status, Pageable pageable);

    // 查询在某个日期范围内的合同
    List<Contract> findByStartDateBetween(LocalDate startDate, LocalDate endDate);
}