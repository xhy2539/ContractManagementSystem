package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.entity.ContractProcessState; // 引入
import com.example.contractmanagementsystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractProcessRepository extends JpaRepository<ContractProcess, Long> {

    List<ContractProcess> findByContract(Contract contract);
    List<ContractProcess> findByContractAndType(Contract contract, Integer type); // type 应该是 ContractProcessType 枚举
    ContractProcess findByContractAndOperatorAndType(Contract contract, User operator, Integer type); // type 应该是 ContractProcessType 枚举
    List<ContractProcess> findByOperator(User operator);
    List<ContractProcess> findByContractAndState(Contract contract, ContractProcessState state); // state 应该是 ContractProcessState 枚举
    List<ContractProcess> findByTypeAndState(Integer type, ContractProcessState state); // type 和 state 应该是枚举

    // 新增方法: 用于 deleteUser 检查
    long countByOperatorAndState(User operator, ContractProcessState state);
}