package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractReminder;
import com.example.contractmanagementsystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReminderRepository extends JpaRepository<ContractReminder, Long> {
    
    /**
     * 根据合同删除所有相关的提醒记录
     */
    void deleteByContract(Contract contract);

    /**
     * 查找用户的未读提醒数量
     */
    long countByUserAndIsReadFalse(User user);

    /**
     * 将指定提醒标记为已读
     */
    @Modifying
    @Query("UPDATE ContractReminder r SET r.isRead = true, r.readAt = :readAt WHERE r.user = :user AND r.id IN :reminderIds")
    int markAsRead(@Param("user") User user, @Param("reminderIds") List<Long> reminderIds, @Param("readAt") LocalDateTime readAt);

    /**
     * 查找过期的提醒记录
     */
    @Query("SELECT r FROM ContractReminder r WHERE r.targetDate < :cutoffDate")
    List<ContractReminder> findExpiredReminders(@Param("cutoffDate") LocalDate cutoffDate);
} 