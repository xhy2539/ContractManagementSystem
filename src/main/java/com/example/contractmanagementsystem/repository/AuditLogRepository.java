package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // 根据操作用户名查找日志 (分页)
    Page<AuditLog> findByUsernameOrderByTimestampDesc(String username, Pageable pageable);

    // 根据操作类型查找日志 (分页)
    Page<AuditLog> findByActionOrderByTimestampDesc(String action, Pageable pageable);

    // 查询某个时间段内的日志
    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime startTime, LocalDateTime endTime);
}