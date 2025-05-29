package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.entity.ContractProcessState;
import com.example.contractmanagementsystem.entity.ContractProcessType; // 引入 ContractProcessType
import com.example.contractmanagementsystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor; // <-- 新增导入

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractProcessRepository extends JpaRepository<ContractProcess, Long>, JpaSpecificationExecutor<ContractProcess> {
    //                                                                                     ^-------------------- 新增此接口

    List<ContractProcess> findByContract(Contract contract);

    // 修改 type 参数为 ContractProcessType 枚举
    List<ContractProcess> findByContractAndType(Contract contract, ContractProcessType type);

    // 修改 type 参数为 ContractProcessType 枚举
    ContractProcess findByContractAndOperatorAndType(Contract contract, User operator, ContractProcessType type);

    List<ContractProcess> findByOperator(User operator);

    // state 参数已经正确使用了 ContractProcessState 枚举
    List<ContractProcess> findByContractAndState(Contract contract, ContractProcessState state);

    // 修改 type 参数为 ContractProcessType 枚举
    List<ContractProcess> findByTypeAndState(ContractProcessType type, ContractProcessState state);

    // 用于 deleteUser 检查
    long countByOperatorAndState(User operator, ContractProcessState state);

    // 您可能还需要一些额外的方法，但当前的 JpaSpecificationExecutor 已经满足了 getPendingProcessesForUser 的需求。

    Optional<ContractProcess> findByContractIdAndOperatorUsernameAndTypeAndState(
            Long contractId, 
            String operatorUsername, 
            ContractProcessType type, 
            ContractProcessState state);
}