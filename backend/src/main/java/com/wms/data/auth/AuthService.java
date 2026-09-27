package com.wms.data.auth;

import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.data.audit.AuditLogService;
import com.wms.data.auth.dto.ChangePasswordRequest;
import com.wms.data.auth.dto.LoginRequest;
import com.wms.data.auth.vo.LoginVO;
import com.wms.data.permission.PermissionLoader;
import com.wms.data.user.UserService;
import com.wms.data.user.vo.UserVO;
import com.wms.domain.entity.User;
import com.wms.security.JwtTokenProvider;
import com.wms.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 认证服务（A-B6）：登录、登出、当前用户、修改本人密码。
 *
 * <p>接口对应 API-001 ~ API-004。
 * 会话策略为**无状态 JWT**：服务端不保存会话，登出仅清空上下文并记审计日志；
 * Token 一旦签发即在有效期内持续可用（《鉴权中间件规范》2.4 已声明该取舍）。
 *
 * @author a
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserService userService;

    private final PermissionLoader permissionLoader;

    private final JwtTokenProvider tokenProvider;

    private final AuditLogService auditLogService;

    /**
     * 构造认证服务。
     *
     * @param userService      用户服务
     * @param permissionLoader 权限装载服务
     * @param tokenProvider    JWT 提供者
     * @param auditLogService  审计日志服务
     */
    public AuthService(UserService userService,
                       PermissionLoader permissionLoader,
                       JwtTokenProvider tokenProvider,
                       AuditLogService auditLogService) {
        this.userService = userService;
        this.permissionLoader = permissionLoader;
        this.tokenProvider = tokenProvider;
        this.auditLogService = auditLogService;
    }

    /**
     * 登录（API-001）：校验账号密码与账号状态，签发 Token 并记录登录时间与审计日志。
     *
     * <p>账号不存在与密码错误返回**同一个错误码** 40102，避免账号枚举。
     *
     * @param request    登录请求
     * @param httpRequest 当前 HTTP 请求（用于记录 IP）
     * @return 登录响应（Token + 用户信息）
     */
    public LoginVO login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userService.findByAccount(request.account());
        if (user == null || !userService.matchesPassword(request.password(), user.getPassword())) {
            log.warn("登录失败 account={} 原因=账号或密码错误", request.account());
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        if (UserService.STATUS_DISABLED.equals(user.getStatus())) {
            log.warn("登录失败 account={} 原因=账号已被禁用", request.account());
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        PermissionLoader.UserAuthority authority = permissionLoader.load(user.getId());
        LoginUser loginUser = new LoginUser(user.getId(), user.getAccount(), user.getName(),
                authority.roleSet(), authority.permissionSet());
        String token = tokenProvider.createToken(loginUser);

        userService.touchLastLogin(user.getId());
        auditLogService.recordLogin(user.getId(), user.getAccount(), httpRequest);
        log.info("登录成功 account={} roles={} 权限数={}", user.getAccount(),
                authority.roles(), authority.permissions().size());

        UserVO userVO = UserVO.from(user, authority.roles(), authority.permissions());
        return LoginVO.of(token, tokenProvider.getTokenExpireSeconds(), userVO);
    }

    /**
     * 登出（API-002）：无状态方案下清空服务端上下文，并记录审计日志。
     *
     * @param loginUser   当前登录用户
     * @param httpRequest 当前 HTTP 请求
     */
    public void logout(LoginUser loginUser, HttpServletRequest httpRequest) {
        if (loginUser != null) {
            auditLogService.recordLogout(loginUser.getUserId(), loginUser.getAccount(), httpRequest);
            log.info("用户登出 account={}", loginUser.getAccount());
        }
    }

    /**
     * 当前用户信息（API-003）：返回角色与权限码，供前端菜单显隐与路由守卫使用。
     *
     * @param loginUser 当前登录用户
     * @return 用户视图对象（含角色与权限）
     */
    public UserVO currentUser(LoginUser loginUser) {
        User user = userService.getByIdOrThrow(loginUser.getUserId());
        return userService.toVO(user);
    }

    /**
     * 修改本人密码（API-004）。
     *
     * @param loginUser 当前登录用户
     * @param request   改密请求
     */
    public void changePassword(LoginUser loginUser, ChangePasswordRequest request) {
        userService.changePassword(loginUser.getUserId(), request.oldPassword(), request.newPassword());
    }
}
