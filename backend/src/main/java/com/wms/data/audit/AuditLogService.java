package com.wms.data.audit;

import com.wms.domain.entity.AuditLog;
import com.wms.domain.mapper.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 操作审计日志服务（《代码规范》4.6）：记录登录、登出、越权等关键路径。
 *
 * <p>写日志失败**不得影响主流程**，因此这里捕获全部异常仅告警。
 * 禁止写入密码、Token 等敏感信息。
 *
 * @author a
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    /** 未知 IP 占位。 */
    private static final String UNKNOWN_IP = "unknown";

    private static final String ACTION_LOGIN = "login";

    private static final String ACTION_LOGOUT = "logout";

    private final AuditLogMapper auditLogMapper;

    /**
     * 构造审计日志服务。
     *
     * @param auditLogMapper 审计日志 Mapper
     */
    public AuditLogService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 记录登录成功。
     *
     * @param userId  用户 id
     * @param account 账号
     * @param request 当前请求
     */
    public void recordLogin(Long userId, String account, HttpServletRequest request) {
        record(userId, account, ACTION_LOGIN, "users", String.valueOf(userId), null, request);
    }

    /**
     * 记录登出。
     *
     * @param userId  用户 id
     * @param account 账号
     * @param request 当前请求
     */
    public void recordLogout(Long userId, String account, HttpServletRequest request) {
        record(userId, account, ACTION_LOGOUT, "users", String.valueOf(userId), null, request);
    }

    /**
     * 记录一条审计日志。
     *
     * @param userId     操作人 id
     * @param account    操作人账号
     * @param action     行为
     * @param resource   资源
     * @param resourceId 资源 id
     * @param detail     详情（禁止包含密码、Token）
     * @param request    当前请求
     */
    public void record(Long userId, String account, String action, String resource,
                       String resourceId, String detail, HttpServletRequest request) {
        try {
            AuditLog entity = new AuditLog();
            entity.setUserId(userId);
            entity.setAccount(account);
            entity.setAction(action);
            entity.setResource(resource);
            entity.setResourceId(resourceId);
            entity.setDetail(detail);
            entity.setIp(resolveClientIp(request));
            auditLogMapper.insert(entity);
        } catch (RuntimeException ex) {
            log.warn("写入审计日志失败 action={} account={} 原因={}", action, account, ex.getMessage());
        }
    }

    /**
     * 解析客户端 IP，兼容反向代理。
     *
     * @param request 当前请求，可为 null
     * @return 客户端 IP
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN_IP;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
            return first.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
