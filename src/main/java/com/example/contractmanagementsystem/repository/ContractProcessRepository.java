package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.entity.ContractProcessState;
import com.example.contractmanagementsystem.entity.ContractProcessType;
import com.example.contractmanagementsystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractProcessRepository extends JpaRepository<ContractProcess, Long>, JpaSpecificationExecutor<ContractProcess> {

    List<ContractProcess> findByContractAndState(Contract contract, ContractProcessState state);

    Optional<ContractProcess> findByContractIdAndOperatorUsernameAndTypeAndState(
            Long contractId, String username, ContractProcessType type, ContractProcessState state
    );

    List<ContractProcess> findByContractAndType(Contract contract, ContractProcessType type);

    List<ContractProcess> findByContractOrderByCreatedAtDesc(Contract contract);

    long countByOperatorAndState(User operator, ContractProcessState state);

    List<ContractProcess> findByContractAndTypeAndState(Contract contract, ContractProcessType type, ContractProcessState state);
   /**
     * 根据合同ID、流程类型和流程状态查找流程记录列表。
     * @param contractId 关联的合同ID
     * @param type 流程类型
     * @param state 流程状态
     * @return 符合条件的流程记录列表
     */
    List<ContractProcess> findByContractIdAndTypeAndState(Long contractId, ContractProcessType type, ContractProcessState state);
    
    /**
     * 根据合同ID删除相关的流程记录。
     *
     * @param contractId 合同ID。
     */
    @Modifying
    @Query("DELETE FROM ContractProcess cp WHERE cp.contract.id = :contractId")
    void deleteByContract(Long contractId);

}