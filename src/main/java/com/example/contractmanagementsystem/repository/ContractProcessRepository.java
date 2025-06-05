package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.entity.ContractProcessState;
import com.example.contractmanagementsystem.entity.ContractProcessType; // <-- 确保这里没有多余的空格，并且路径正确
import com.example.contractmanagementsystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractProcessRepository extends JpaRepository<ContractProcess, Long>, JpaSpecificationExecutor<ContractProcess> {

    // --- 你原有方法（保持不变） ---
    List<ContractProcess> findByContract(Contract contract);

    List<ContractProcess> findByContractAndType(Contract contract, ContractProcessType type);

    ContractProcess findByContractAndOperatorAndType(Contract contract, User operator, ContractProcessType type);

    List<ContractProcess> findByOperator(User operator);

    List<ContractProcess> findByContractAndState(Contract contract, ContractProcessState state);

    List<ContractProcess> findByTypeAndState(ContractProcessType type, ContractProcessState state);

    long countByOperatorAndState(User operator, ContractProcessState state);

    // --- 确保这个方法也存在，因为它在 ContractController 中使用了类似查询 ---
    Optional<ContractProcess> findByContractIdAndOperatorUsernameAndTypeAndState(
            Long contractId,
            String operatorUsername,
            ContractProcessType type,
            ContractProcessState state);

    // --- 新增方法 (为了 ContractApprovalServiceImp 中的 canUserApproveContract 逻辑更健壮，可选) ---
    List<ContractProcess> findByContract_IdAndTypeAndStateAndOperator_Username(
            Long contractId,
            ContractProcessType type,
            ContractProcessState state,
            String operatorUsername
    );

    /**
     * 根据合同查找所有处理记录，按创建时间倒序排序
     */
    List<ContractProcess> findByContractOrderByCreatedAtDesc(Contract contract);

    /**
     * 查找指定合同的所有处理记录
     */
    List<ContractProcess> findByContract(Contract contract);
}