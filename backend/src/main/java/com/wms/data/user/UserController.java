package com.wms.data.user;

import com.wms.common.ApiResponse;
import com.wms.common.PageResult;
import com.wms.data.user.dto.CreateUserRequest;
import com.wms.data.user.dto.UpdateUserRequest;
import com.wms.data.user.dto.UpdateUserStatusRequest;
import com.wms.data.user.vo.UserItemVO;
import com.wms.data.user.vo.UserVO;
import com.wms.security.LoginUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户控制器，对应《接口文档》API-005 ~ API-009。
 *
 * <p>全部接口需要 {@code user:manage} 权限（仅 admin），由 a 的鉴权中间件统一强制校验（A-B8）。
 *
 * @author a
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAuthority('user:manage')")
public class UserController {

    private final UserService userService;

    /**
     * 构造用户控制器。
     *
     * @param userService 用户服务
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * API-005 创建用户（管理员注册用户）。
     *
     * @param request   创建请求
     * @param loginUser 当前登录用户（记录 created_by）
     * @return 新建用户信息（含角色与权限）
     */
    @PostMapping
    public ApiResponse<UserVO> create(@RequestBody @Valid CreateUserRequest request,
                                      @AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(userService.createUser(request, operatorId(loginUser)));
    }

    /**
     * API-006 用户列表（分页、可按状态过滤）。
     *
     * @param page     页码，默认 1
     * @param pageSize 每页条数，默认 20、上限 100
     * @param status   账号状态过滤：active / disabled
     * @param keyword  账号或姓名关键字
     * @return 分页结果
     */
    @GetMapping
    public ApiResponse<PageResult<UserItemVO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(userService.listUsers(page, pageSize, status, keyword));
    }

    /**
     * API-007 更新用户（姓名、邮箱、手机号、备注、关联角色）。
     *
     * @param id        用户 id
     * @param request   更新请求
     * @param loginUser 当前登录用户
     * @return 更新后的用户信息
     */
    @PutMapping("/{id}")
    public ApiResponse<UserVO> update(@PathVariable Long id,
                                      @RequestBody @Valid UpdateUserRequest request,
                                      @AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(userService.updateUser(id, request, operatorId(loginUser)));
    }

    /**
     * API-008 启用/禁用用户。
     *
     * @param id        用户 id
     * @param request   状态请求
     * @param loginUser 当前登录用户
     * @return 更新后的用户信息
     */
    @PutMapping("/{id}/status")
    public ApiResponse<UserVO> updateStatus(@PathVariable Long id,
                                            @RequestBody @Valid UpdateUserStatusRequest request,
                                            @AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(userService.updateStatus(id, request.status(), operatorId(loginUser)));
    }

    /**
     * API-009 删除用户。
     *
     * @param id        用户 id
     * @param loginUser 当前登录用户
     * @return 无数据的成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @AuthenticationPrincipal LoginUser loginUser) {
        userService.deleteUser(id, operatorId(loginUser));
        return ApiResponse.ok();
    }

    /**
     * 取当前操作人 id。
     *
     * @param loginUser 当前登录用户
     * @return 用户 id，未登录时返回 null
     */
    private Long operatorId(LoginUser loginUser) {
        return loginUser == null ? null : loginUser.getUserId();
    }
}
