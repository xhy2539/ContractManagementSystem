package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.dto.DashboardStatsDto;
import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.entity.ContractProcessState;
import com.example.contractmanagementsystem.entity.ContractProcessType;
import com.example.contractmanagementsystem.entity.ContractStatus;
import com.example.contractmanagementsystem.entity.Customer;
import com.example.contractmanagementsystem.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long>, JpaSpecificationExecutor<Contract> {

    Optional<Contract> findByContractNumber(String contractNumber);
    Page<Contract> findByContractNameContainingIgnoreCase(String contractName, Pageable pageable);
    List<Contract> findByCustomer(Customer customer);
    List<Contract> findByDrafter(User drafter);
    Page<Contract> findByStatus(ContractStatus status, Pageable pageable);
    List<Contract> findByStartDateBetween(LocalDate startDate, LocalDate endDate);
    long countByDrafter(User drafter);

    // New: Count contracts by customer
    @Query("SELECT c.customer.customerName, COUNT(c) FROM Contract c GROUP BY c.customer.customerName")
    List<Object[]> findContractCountByCustomer();

    // New: Query contracts by contract name and number combined
    @Query("SELECT c FROM Contract c WHERE " +
            "(:contractName IS NULL OR LOWER(c.contractName) LIKE LOWER(CONCAT('%', :contractName, '%'))) AND " +
            "(:contractNumber IS NULL OR LOWER(c.contractNumber) LIKE LOWER(CONCAT('%', :contractNumber, '%')))")
    Page<Contract> findByContractNameAndNumberContaining(String contractName, String contractNumber, Pageable pageable);

    // New: Query contracts by status and other conditions combined
    @Query("SELECT c FROM Contract c WHERE " +
            "(:status IS NULL OR c.status = :status) AND " +
            "(:contractName IS NULL OR LOWER(c.contractName) LIKE LOWER(CONCAT('%', :contractName, '%'))) AND " +
            "(:contractNumber IS NULL OR LOWER(c.contractNumber) LIKE LOWER(CONCAT('%', :contractNumber, '%')))")
    Page<Contract> findByStatusAndOtherConditions(ContractStatus status, String contractName, String contractNumber, Pageable pageable);

    // New: Count contracts by status
    long countByStatus(ContractStatus status);

    /**
     * Example: A more precise method for querying contracts pending assignment (using JPQL)
     * This method filters for contracts with status DRAFT or PENDING_ASSIGNMENT that have no associated ContractProcess records.
     * @param statuses List of contract statuses to filter by
     * @param pageable Pagination parameters
     * @return Paginated contract data
     */
    @Query("SELECT c FROM Contract c WHERE c.status IN :statuses AND NOT EXISTS (SELECT cp FROM ContractProcess cp WHERE cp.contract = c)")
    Page<Contract> findContractsForAssignmentByStatusAndNoProcess(List<ContractStatus> statuses, Pageable pageable);

    /**
     * Example: Query contracts pending assignment with search filters
     * @param statuses List of statuses
     * @param contractNameSearch Keyword for contract name (ignored if null or empty)
     * @param contractNumberSearch Keyword for contract number (ignored if null or empty)
     * @param pageable Pagination parameters
     * @return Paginated contract data
     */
    @Query("SELECT DISTINCT c FROM Contract c " +
            "LEFT JOIN FETCH c.customer cust " +          // Eager fetch customer
            "LEFT JOIN FETCH c.drafter dft " +            // Eager fetch drafter (User)
            "LEFT JOIN FETCH dft.roles contract_drafter_roles " +  // Eager fetch drafter's roles collection
            // "LEFT JOIN FETCH contract_drafter_roles.functionalities " + // 预先抓取这些 roles 的 functionalities 集合 - 暂时注释掉，因为 Role 实体中可能缺少 functionalities 属性
            "WHERE c.status IN :statuses " +
            "AND (:contractNameSearch IS NULL OR LOWER(c.contractName) LIKE LOWER(CONCAT('%', :contractNameSearch, '%'))) " +
            "AND (:contractNumberSearch IS NULL OR LOWER(c.contractNumber) LIKE LOWER(CONCAT('%', :contractNumberSearch, '%'))) " +
            "AND NOT EXISTS (SELECT cp FROM ContractProcess cp WHERE cp.contract = c)")
    Page<Contract> findContractsForAssignmentWithFilters(List<ContractStatus> statuses, String contractNameSearch, String contractNumberSearch, Pageable pageable);

    @Query("SELECT c.status, COUNT(c) FROM Contract c GROUP BY c.status")
    List<Object[]> findContractCountByStatus();


    @Query("SELECT c FROM Contract c LEFT JOIN FETCH c.customer LEFT JOIN FETCH c.drafter d LEFT JOIN FETCH d.roles r LEFT JOIN FETCH r.functionalities WHERE c.id = :id")
    Optional<Contract> findByIdWithCustomerAndDrafter(@Param("id") Long id); // 方法名也更改为更具描述性的清晰度
    /**
     * New: Used to find contracts of a specific status where the end date is less than or equal to a given date.
     * Changed from findByStatusAndEndDateBeforeOrEqual to resolve ambiguity.
     */
    List<Contract> findByStatusAndEndDateLessThanEqual(ContractStatus status, LocalDate endDate);

    /**
     * 根据合同ID删除相关的流程记录。
     *
     * @param id 合同ID。
     */
    @Modifying
    @Query("DELETE FROM Contract c WHERE c.id = :id")
    void deleteById(@Param("id") Long id);

    /**
     * 查找在指定日期范围内即将到期的有效合同。
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 即将到期的有效合同列表
     */
    @Query("SELECT c FROM Contract c WHERE c.status = 'ACTIVE' AND c.endDate BETWEEN :startDate AND :endDate")
    List<Contract> findActiveContractsExpiringBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    /**
     * 在一次查询中获取仪表盘的所有统计数据。
     * @param today 当前日期
     * @param futureDate 即将到期的截止日期 (例如，30天后)
     * @param inProcessStatuses 流程中的状态列表
     * @param user 当前用户，如果需要按用户过滤
     * @return 包含所有统计数据的DTO
     */
    @Query("SELECT new com.example.contractmanagementsystem.dto.DashboardStatsDto(" +
            "COALESCE(SUM(CASE WHEN c.status = 'ACTIVE' THEN 1L ELSE 0L END), 0L), " +
            "COALESCE(SUM(CASE WHEN c.status = 'ACTIVE' AND c.endDate BETWEEN :today AND :futureDate THEN 1L ELSE 0L END), 0L), " +
            "COALESCE(SUM(CASE WHEN c.status = 'EXPIRED' THEN 1L ELSE 0L END), 0L), " +
            "COALESCE(SUM(CASE WHEN c.status IN :inProcessStatuses THEN 1L ELSE 0L END), 0L), " +
            "COALESCE(SUM(CASE WHEN c.status = 'PENDING_ASSIGNMENT' THEN 1L ELSE 0L END), 0L)) " +
            "FROM Contract c " +
            // 如果需要根据非管理员用户进行过滤，则添加WHERE子句
            "WHERE :user IS NULL OR c.drafter = :user OR EXISTS (" +
            "SELECT 1 FROM ContractProcess cp WHERE cp.contract = c AND cp.operator = :user)")
    DashboardStatsDto getDashboardStatistics(@Param("today") LocalDate today,
                                             @Param("futureDate") LocalDate futureDate,
                                             @Param("inProcessStatuses") List<ContractStatus> inProcessStatuses,
                                             @Param("user") User user);

    // 为管理员提供一个不过滤用户的版本
    @Query("SELECT new com.example.contractmanagementsystem.dto.DashboardStatsDto(" +
            "COALESCE(SUM(CASE WHEN c.status = 'ACTIVE' THEN 1L ELSE 0L END), 0L), " +
            "COALESCE(SUM(CASE WHEN c.status = 'ACTIVE' AND c.endDate BETWEEN :today AND :futureDate THEN 1L ELSE 0L END), 0L), " +
            "COALESCE(SUM(CASE WHEN c.status = 'EXPIRED' THEN 1L ELSE 0L END), 0L), " +
            "COALESCE(SUM(CASE WHEN c.status IN :inProcessStatuses THEN 1L ELSE 0L END), 0L), " +
            "COALESCE(SUM(CASE WHEN c.status = 'PENDING_ASSIGNMENT' THEN 1L ELSE 0L END), 0L)) " +
            "FROM Contract c")
    DashboardStatsDto getDashboardStatisticsForAdmin(@Param("today") LocalDate today,
                                                     @Param("futureDate") LocalDate futureDate,
                                                     @Param("inProcessStatuses") List<ContractStatus> inProcessStatuses);



    @Modifying
    @Query("UPDATE Contract c SET c.status = 'EXPIRED', c.updatedAt = :now WHERE c.endDate < :today AND c.status <> 'EXPIRED'")
    int updateStatusForExpiredContracts(@Param("now") LocalDateTime now, @Param("today") LocalDate today);

    /**
     * 优化的查询方法：获取待定稿合同（包含必要的关联数据）
     */
    @Query("SELECT DISTINCT c FROM Contract c " +
           "LEFT JOIN FETCH c.customer " +
           "LEFT JOIN FETCH c.drafter d " +
           "LEFT JOIN FETCH d.roles " +
           "WHERE c.status = 'PENDING_FINALIZATION' " +
           "AND (:contractNameSearch IS NULL OR LOWER(c.contractName) LIKE LOWER(CONCAT('%', :contractNameSearch, '%'))) " +
           "AND EXISTS (SELECT cp FROM ContractProcess cp WHERE cp.contract = c AND cp.operator = :user AND cp.type = 'FINALIZE' AND cp.state = 'PENDING')")
    Page<Contract> findContractsPendingFinalizationForUserOptimized(@Param("user") User user, 
                                                                   @Param("contractNameSearch") String contractNameSearch, 
                                                                   Pageable pageable);



}
