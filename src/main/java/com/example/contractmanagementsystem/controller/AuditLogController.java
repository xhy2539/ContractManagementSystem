package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.entity.AuditLog;
// import com.example.contractmanagementsystem.repository.AuditLogRepository; // 不再直接使用此Repository
import com.example.contractmanagementsystem.service.AuditLogService; // 引入AuditLogService
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.supercsv.io.CsvBeanWriter;
import org.supercsv.prefs.CsvPreference;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/system/audit-logs")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService; // 修改为注入AuditLogService

    @Autowired
    public AuditLogController(AuditLogService auditLogService) { // 修改构造器
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LOG_VIEW_AUDIT')") // 使用功能编号
    public ResponseEntity<Page<AuditLog>> getAllLogs(
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        // 调用AuditLogService中实现组合查询的方法
        Page<AuditLog> logs = auditLogService.searchLogs(username, action, startDate, endDate, pageable);
        return ResponseEntity.ok(logs);
    }


    @GetMapping("/export")
    @PreAuthorize("hasAuthority('LOG_EXPORT_AUDIT')") // 使用功能编号
    public void exportAuditLogsToCsv(HttpServletResponse response,
                                     @RequestParam(required = false) String username,
                                     @RequestParam(required = false) String action,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) throws IOException {

        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8"); // 确保响应编码为 UTF-8

        // 添加 BOM 头，帮助 Excel 正确识别 UTF-8 编码的中文字符
        response.getWriter().write('\uFEFF');

        DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String currentDateTime = dateFormatter.format(new Date());
        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=audit_logs_" + currentDateTime + ".csv";
        response.setHeader(headerKey, headerValue);

        // 调用AuditLogService中获取所有符合条件日志的方法（不分页，用于导出）
        List<AuditLog> listLogs = auditLogService.findAllLogsByCriteria(username, action, startDate, endDate);

        try (CsvBeanWriter csvWriter = new CsvBeanWriter(response.getWriter(), CsvPreference.STANDARD_PREFERENCE)) {
            String[] csvHeader = {"日志ID", "操作用户", "操作类型", "操作详情", "时间戳"};
            String[] nameMapping = {"id", "username", "action", "details", "timestamp"};

            csvWriter.writeHeader(csvHeader);

            if (listLogs != null) {
                for (AuditLog log : listLogs) {
                    csvWriter.write(log, nameMapping);
                }
            }
        }
    }
}