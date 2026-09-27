package com.wms.data.auth;

import com.wms.common.ApiResponse;
import com.wms.data.auth.dto.ChangePasswordRequest;
import com.wms.data.auth.dto.LoginRequest;
import com.wms.data.auth.vo.LoginVO;
import com.wms.data.user.vo.UserVO;
import com.wms.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器（A-B6），对应《接口文档》第 3 节 API-001 ~ API-004。
 *
 * <p>本控制器只做协议转换与参数校验，业务逻辑在 {@link AuthService}（《代码规范》4.1）。
 *
 * @author a
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * 构造认证控制器。
     *
     * @param authService 认证服务
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * API-001 账号密码登录，返回 Token。
     *
     * <p>权限：公开（唯一免鉴权接口，见《鉴权中间件规范》2.1）。
     *
     * @param request     登录请求
     * @param httpRequest 当前 HTTP 请求
     * @return 登录响应
     */
    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@RequestBody @Valid LoginRequest request,
                                      HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(request, httpRequest));
    }

    /**
     * API-002 登出，注销会话。
     *
     * <p>权限：登录即可。
     *
     * @param loginUser   当前登录用户
     * @param httpRequest 当前 HTTP 请求
     * @return 无数据的成功响应
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal LoginUser loginUser,
                                    HttpServletRequest httpRequest) {
        authService.logout(loginUser, httpRequest);
        return ApiResponse.ok();
    }

    /**
     * API-003 当前用户信息（含角色与权限列表）。
     *
     * <p>权限：登录即可。
     *
     * @param loginUser 当前登录用户
     * @return 用户信息
     */
    @GetMapping("/me")
    public ApiResponse<UserVO> me(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(authService.currentUser(loginUser));
    }

    /**
     * API-004 修改本人密码。
     *
     * <p>权限：登录即可（只能改自己的密码）。
     *
     * @param loginUser 当前登录用户
     * @param request   改密请求
     * @return 无数据的成功响应
     */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal LoginUser loginUser,
                                            @RequestBody @Valid ChangePasswordRequest request) {
        authService.changePassword(loginUser, request);
        return ApiResponse.ok();
    }
}
