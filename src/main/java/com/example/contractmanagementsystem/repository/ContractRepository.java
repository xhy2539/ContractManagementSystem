package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractStatus; // 引入
import com.example.contractmanagementsystem.entity.Customer;
import com.example.contractmanagementsystem.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long>, JpaSpecificationExecutor<Contract> {

    Optional<Contract> findByContractNumber(String contractNumber);
    Page<Contract> findByContractNameContainingIgnoreCase(String contractName, Pageable pageable);
    List<Contract> findByCustomer(Customer customer);
    List<Contract> findByDrafter(User drafter);
    Page<Contract> findByStatus(ContractStatus status, Pageable pageable); // status 应该是 ContractStatus 枚举
    List<Contract> findByStartDateBetween(LocalDate startDate, LocalDate endDate);

    // 新增方法: 用于 deleteUser 检查
    long countByDrafter(User drafter);
    // 可选的更具体的检查:
    // long countByDrafterAndStatusIn(User drafter, List<ContractStatus> statuses);
}