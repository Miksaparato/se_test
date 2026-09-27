package com.wms.data.role;

import com.wms.common.ApiResponse;
import com.wms.data.role.dto.AssignPermissionsRequest;
import com.wms.data.role.dto.CreateRoleRequest;
import com.wms.data.role.dto.UpdateRoleRequest;
import com.wms.data.role.vo.RoleVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色控制器，对应《接口文档》API-010 ~ API-014。
 *
 * <p>全部接口需要 {@code user:manage} 权限（仅 admin）。
 *
 * @author a
 */
@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("hasAuthority('user:manage')")
public class RoleController {

    private final RoleService roleService;

    /**
     * 构造角色控制器。
     *
     * @param roleService 角色服务
     */
    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * API-010 角色列表（含内置四角色）。
     *
     * @return 角色列表
     */
    @GetMapping
    public ApiResponse<List<RoleVO>> list() {
        return ApiResponse.ok(roleService.listRoles());
    }

    /**
     * API-011 创建角色。
     *
     * @param request 创建请求
     * @return 新建角色
     */
    @PostMapping
    public ApiResponse<RoleVO> create(@RequestBody @Valid CreateRoleRequest request) {
        return ApiResponse.ok(roleService.createRole(request));
    }

    /**
     * API-012 更新角色名称/描述。
     *
     * @param id      角色 id
     * @param request 更新请求
     * @return 更新后的角色
     */
    @PutMapping("/{id}")
    public ApiResponse<RoleVO> update(@PathVariable Long id,
                                      @RequestBody @Valid UpdateRoleRequest request) {
        return ApiResponse.ok(roleService.updateRole(id, request));
    }

    /**
     * API-013 删除角色（内置角色与仍被引用的角色不可删除）。
     *
     * @param id 角色 id
     * @return 无数据的成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ApiResponse.ok();
    }

    /**
     * API-014 为角色分配权限（菜单 + 操作），全量覆盖语义。
     *
     * @param id      角色 id
     * @param request 权限码列表
     * @return 更新后的角色
     */
    @PutMapping("/{id}/permissions")
    public ApiResponse<RoleVO> assignPermissions(@PathVariable Long id,
                                                 @RequestBody @Valid AssignPermissionsRequest request) {
        return ApiResponse.ok(roleService.assignPermissions(id, request.permissions()));
    }
}
