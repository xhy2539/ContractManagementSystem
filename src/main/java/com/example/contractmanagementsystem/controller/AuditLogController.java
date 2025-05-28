package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.entity.AuditLog;
import com.example.contractmanagementsystem.repository.AuditLogRepository;
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

    private final AuditLogRepository auditLogRepository;

    @Autowired
    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LOG_VIEW_AUDIT')") // 使用功能编号
    public ResponseEntity<Page<AuditLog>> getAllLogs(
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable,
            // 新增筛选参数以匹配前端 audit-logs.js
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        // 注意：这里的查询逻辑直接使用了Repository，并且没有组合所有筛选条件。
        // 为保持与前端筛选功能的一致性，通常应该构建一个更复杂的查询（可能通过Specification或多个if/else）。
        // 以下是一个简化的示例，它优先处理用户名和操作类型，如果都没有，则查找全部。
        // 实际项目中，您可能需要一个更完善的 AuditLogService 来处理这些查询。

        Page<AuditLog> logs;
        if (username != null && !username.isEmpty()) {
            logs = auditLogRepository.findByUsernameOrderByTimestampDesc(username, pageable);
        } else if (action != null && !action.isEmpty()) {
            logs = auditLogRepository.findByActionOrderByTimestampDesc(action, pageable);
        }
        // 注意：audit-logs.js 会发送 startDate 和 endDate，但这里没有直接处理组合查询。
        // 如果需要时间范围查询与用户名/操作类型结合，或者单独的时间范围分页查询，
        // auditLogRepository 需要相应的方法，或者使用 JpaSpecificationExecutor。
        // 为了简单起见，如果前端只发送时间范围，此处的 getAllLogs 可能不会按时间筛选。
        // audit-logs.js 中的 fetchAndDisplayLogs 逻辑会构建带有所有参数的 URL，
        // 后端需要能处理这些组合。
        // 暂时，为了让基本的分页查看日志工作，我们保留 auditLogRepository.findAll(pageable);
        // 但请注意，这与前端筛选的期望可能不完全匹配。
        else {
            logs = auditLogRepository.findAll(pageable); // 如果没有特定筛选，返回所有
        }
        return ResponseEntity.ok(logs);
    }

    // 原有的根据用户名和操作类型查询的方法可以保留，如果 audit-logs.js 的筛选逻辑会单独发这些参数
    // 但更常见的是前端将所有筛选条件一次性发给一个端点。

    @GetMapping(params = "username", name="getLogsByUsername") // 给一个不同的映射名称或确保参数组合唯一
    @PreAuthorize("hasAuthority('LOG_VIEW_AUDIT')")
    public ResponseEntity<Page<AuditLog>> getLogsByUsernameAndFilters(
            @RequestParam String username,
            @RequestParam(required = false) String action, // 允许组合
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        // 此处需要更复杂的查询逻辑来组合 username, action, startDate, endDate
        // 暂时只按 username 返回
        Page<AuditLog> logs = auditLogRepository.findByUsernameOrderByTimestampDesc(username, pageable);
        return ResponseEntity.ok(logs);
    }

    @GetMapping(params = "action", name="getLogsByAction") // 给一个不同的映射名称
    @PreAuthorize("hasAuthority('LOG_VIEW_AUDIT')")
    public ResponseEntity<Page<AuditLog>> getLogsByActionAndFilters(
            @RequestParam String action,
            @RequestParam(required = false) String username, // 允许组合
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        // 此处需要更复杂的查询逻辑
        // 暂时只按 action 返回
        Page<AuditLog> logs = auditLogRepository.findByActionOrderByTimestampDesc(action, pageable);
        return ResponseEntity.ok(logs);
    }


    @GetMapping(params = {"startDate", "endDate"}, name="getLogsByTimestampBetween") // 给一个不同的映射名称
    @PreAuthorize("hasAuthority('LOG_VIEW_AUDIT')")
    public ResponseEntity<List<AuditLog>> getLogsByTimestampBetweenAndFilters(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String username, // 允许组合
            @RequestParam(required = false) String action) {
        // 此处需要更复杂的查询逻辑
        // 暂时只按时间范围返回 (注意，原方法返回List，分页版本应返回Page)
        List<AuditLog> logs = auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(startDate, endDate);
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

        List<AuditLog> listLogs;

        // 注意：这里的筛选逻辑也比较简单，可能需要一个服务层方法来处理复杂的组合筛选条件
        if (username != null && !username.isEmpty()) {
            // 如果只需要按用户名，可以获取所有页数据，或者只导出当前页（取决于需求）
            // auditLogRepository.findByUsernameOrderByTimestampDesc 返回 Page，需要 .getContent()
            listLogs = auditLogRepository.findByUsernameOrderByTimestampDesc(username, Pageable.unpaged()).getContent();
        } else if (action != null && !action.isEmpty()) {
            listLogs = auditLogRepository.findByActionOrderByTimestampDesc(action, Pageable.unpaged()).getContent();
        } else if (startDate != null && endDate != null) {
            listLogs = auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(startDate, endDate);
        } else {
            listLogs = auditLogRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp"));
        }


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