package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.ContractReminder;
import com.example.contractmanagementsystem.entity.ContractReminder.ReminderType;
import com.example.contractmanagementsystem.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 合同提醒服务接口
 * 提供合同到期提醒、续签提醒等智能提醒功能
 */
public interface ContractReminderService {

    /**
     * 创建合同到期提醒
     * @param contractId 合同ID
     * @param daysBefore 提前多少天提醒
     * @return 创建的提醒列表
     */
    List<ContractReminder> createExpirationReminders(Long contractId, Integer daysBefore);

    /**
     * 创建合同续签提醒
     * @param contractId 合同ID
     * @param daysBefore 提前多少天提醒
     * @return 创建的提醒列表
     */
    List<ContractReminder> createRenewalReminders(Long contractId, Integer daysBefore);

    /**
     * 批量扫描并创建即将到期的合同提醒
     * @param daysBefore 提前多少天提醒
     * @return 创建的提醒数量
     */
    int scanAndCreateExpirationReminders(Integer daysBefore);

    /**
     * 发送待发送的提醒
     * @return 发送的提醒数量
     */
    int sendPendingReminders();

    /**
     * 获取用户的未读提醒
     * @param userId 用户ID
     * @return 未读提醒列表
     */
    List<ContractReminder> getUnreadReminders(Long userId);

    /**
     * 获取用户的所有提醒（分页）
     * @param userId 用户ID
     * @param pageable 分页参数
     * @return 提醒分页列表
     */
    Page<ContractReminder> getUserReminders(Long userId, Pageable pageable);

    /**
     * 标记提醒为已读
     * @param userId 用户ID
     * @param reminderIds 提醒ID列表
     * @return 标记成功的数量
     */
    int markRemindersAsRead(Long userId, List<Long> reminderIds);

    /**
     * 删除过期的提醒
     * @param daysOld 多少天前的提醒算过期
     * @return 删除的提醒数量
     */
    int cleanupExpiredReminders(int daysOld);

    /**
     * 获取用户未读提醒数量
     * @param userId 用户ID
     * @return 未读提醒数量
     */
    long getUnreadReminderCount(Long userId);

    /**
     * 取消合同的所有提醒
     * @param contractId 合同ID
     */
    void cancelContractReminders(Long contractId);
} 