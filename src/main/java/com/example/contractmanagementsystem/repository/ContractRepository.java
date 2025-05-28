package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractStatus;
import com.example.contractmanagementsystem.entity.Customer;
import com.example.contractmanagementsystem.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor; // 确保导入
import org.springframework.data.jpa.repository.Query; // 可选，用于复杂查询
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long>, JpaSpecificationExecutor<Contract> { // 继承 JpaSpecificationExecutor

    Optional<Contract> findByContractNumber(String contractNumber);
    Page<Contract> findByContractNameContainingIgnoreCase(String contractName, Pageable pageable);
    List<Contract> findByCustomer(Customer customer);
    List<Contract> findByDrafter(User drafter);
    Page<Contract> findByStatus(ContractStatus status, Pageable pageable);
    List<Contract> findByStartDateBetween(LocalDate startDate, LocalDate endDate);
    long countByDrafter(User drafter);

    // 新增：按客户统计合同数量
    @Query("SELECT c.customer.customerName, COUNT(c) FROM Contract c GROUP BY c.customer.customerName")
    List<Object[]> findContractCountByCustomer();

    // 新增：根据合同名称和编号组合查询
    @Query("SELECT c FROM Contract c WHERE " +
           "(:contractName IS NULL OR LOWER(c.contractName) LIKE LOWER(CONCAT('%', :contractName, '%'))) AND " +
           "(:contractNumber IS NULL OR LOWER(c.contractNumber) LIKE LOWER(CONCAT('%', :contractNumber, '%')))")
    Page<Contract> findByContractNameAndNumberContaining(String contractName, String contractNumber, Pageable pageable);

    // 新增：根据状态和其他条件组合查询
    @Query("SELECT c FROM Contract c WHERE " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(:contractName IS NULL OR LOWER(c.contractName) LIKE LOWER(CONCAT('%', :contractName, '%'))) AND " +
           "(:contractNumber IS NULL OR LOWER(c.contractNumber) LIKE LOWER(CONCAT('%', :contractNumber, '%')))")
    Page<Contract> findByStatusAndOtherConditions(ContractStatus status, String contractName, String contractNumber, Pageable pageable);

    // 新增：查询特定状态的合同数量
    long countByStatus(ContractStatus status);

    /**
     * 示例：一个更精确的查询待分配合同的方法（使用JPQL）
     * 这个方法会筛选出状态为 DRAFT 或 PENDING_ASSIGNMENT，并且没有任何关联 ContractProcess 记录的合同。
     * @param statuses 要筛选的合同状态列表
     * @param pageable 分页参数
     * @return 分页的合同数据
     */
    @Query("SELECT c FROM Contract c WHERE c.status IN :statuses AND NOT EXISTS (SELECT cp FROM ContractProcess cp WHERE cp.contract = c)")
    Page<Contract> findContractsForAssignmentByStatusAndNoProcess(List<ContractStatus> statuses, Pageable pageable);

    /**
     * 示例：包含搜索条件的待分配合同查询
     * @param statuses 状态列表
     * @param contractNameSearch 合同名称关键词 (如果为null或空则忽略)
     * @param contractNumberSearch 合同编号关键词 (如果为null或空则忽略)
     * @param pageable 分页参数
     * @return 分页的合同数据
     */
    @Query("SELECT c FROM Contract c WHERE c.status IN :statuses " +
            "AND (:contractNameSearch IS NULL OR LOWER(c.contractName) LIKE LOWER(CONCAT('%', :contractNameSearch, '%'))) " +
            "AND (:contractNumberSearch IS NULL OR LOWER(c.contractNumber) LIKE LOWER(CONCAT('%', :contractNumberSearch, '%'))) " +
            "AND NOT EXISTS (SELECT cp FROM ContractProcess cp WHERE cp.contract = c)")
    Page<Contract> findContractsForAssignmentWithFilters(List<ContractStatus> statuses, String contractNameSearch, String contractNumberSearch, Pageable pageable);

}