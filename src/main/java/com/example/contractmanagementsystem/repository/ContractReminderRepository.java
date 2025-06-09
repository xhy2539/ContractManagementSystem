package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractReminder;
import com.example.contractmanagementsystem.entity.ContractReminder.ReminderType;
import com.example.contractmanagementsystem.entity.ContractReminder.ReminderStatus;
import com.example.contractmanagementsystem.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ContractReminderRepository extends JpaRepository<ContractReminder, Long> {

    /**
     * 查找指定用户的未读提醒
     */
    List<ContractReminder> findByUserAndIsReadFalseOrderByCreatedAtDesc(User user);

    /**
     * 查找指定用户的所有提醒
     */
    Page<ContractReminder> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    /**
     * 查找需要发送的提醒（今天或之前的，且未发送的）
     */
    @Query("SELECT cr FROM ContractReminder cr WHERE cr.reminderDate <= :today AND cr.isSent = false AND cr.status = 'PENDING'")
    List<ContractReminder> findPendingRemindersForToday(@Param("today") LocalDate today);

    /**
     * 查找指定类型的未发送提醒
     */
    List<ContractReminder> findByReminderTypeAndIsSentFalse(ReminderType reminderType);

    /**
     * 查找指定合同的所有提醒
     */
    List<ContractReminder> findByContractOrderByCreatedAtDesc(Contract contract);

    /**
     * 查找指定合同和类型的提醒
     */
    List<ContractReminder> findByContractAndReminderType(Contract contract, ReminderType reminderType);

    /**
     * 查找指定用户指定状态的提醒数量
     */
    long countByUserAndStatus(User user, ReminderStatus status);

    /**
     * 查找指定用户的未读提醒数量
     */
    long countByUserAndIsReadFalse(User user);

    /**
     * 批量标记提醒为已读
     */
    @Modifying
    @Query("UPDATE ContractReminder cr SET cr.isRead = true, cr.readAt = :readAt WHERE cr.user = :user AND cr.id IN :reminderIds")
    int markAsRead(@Param("user") User user, @Param("reminderIds") List<Long> reminderIds, @Param("readAt") LocalDateTime readAt);

    /**
     * 批量标记提醒为已发送
     */
    @Modifying
    @Query("UPDATE ContractReminder cr SET cr.isSent = true, cr.sentAt = :sentAt, cr.status = 'SENT' WHERE cr.id IN :reminderIds")
    int markAsSent(@Param("reminderIds") List<Long> reminderIds, @Param("sentAt") LocalDateTime sentAt);

    /**
     * 删除指定合同的所有提醒
     */
    void deleteByContract(Contract contract);

    /**
     * 查找即将到期需要创建提醒的合同
     */
    @Query("SELECT c FROM Contract c WHERE c.status = 'ACTIVE' AND c.endDate BETWEEN :startDate AND :endDate " +
           "AND NOT EXISTS (SELECT cr FROM ContractReminder cr WHERE cr.contract = c AND cr.reminderType = :reminderType AND cr.targetDate = c.endDate)")
    List<Contract> findContractsNeedingReminders(@Param("startDate") LocalDate startDate, 
                                                 @Param("endDate") LocalDate endDate, 
                                                 @Param("reminderType") ReminderType reminderType);

    /**
     * 查找过期的提醒（可以清理）
     */
    @Query("SELECT cr FROM ContractReminder cr WHERE cr.targetDate < :cutoffDate AND cr.status IN ('SENT', 'READ', 'DISMISSED')")
    List<ContractReminder> findExpiredReminders(@Param("cutoffDate") LocalDate cutoffDate);
} 