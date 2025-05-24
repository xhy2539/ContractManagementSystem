package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractProcessRepository extends JpaRepository<ContractProcess, Long> {

    // 根据合同查找所有流程记录
    List<ContractProcess> findByContract(Contract contract);

    // 根据合同和操作类型查找流程记录
    List<ContractProcess> findByContractAndType(Contract contract, Integer type);

    // 根据合同、操作人和操作类型查找特定的流程记录 (例如，一个用户在一个合同中可能只负责一种类型的操作)
    ContractProcess findByContractAndOperatorAndType(Contract contract, User operator, Integer type);

    // 根据操作人查找其所有相关的流程记录
    List<ContractProcess> findByOperator(User operator);

    // 根据合同和状态查找流程记录
    List<ContractProcess> findByContractAndState(Contract contract, Integer state);

    // 根据操作类型和状态查找流程记录
    List<ContractProcess> findByTypeAndState(Integer type, Integer state);
}