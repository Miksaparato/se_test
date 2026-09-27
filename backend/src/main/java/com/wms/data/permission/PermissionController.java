package com.wms.data.permission;

import com.wms.common.ApiResponse;
import com.wms.data.permission.vo.PermissionVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限控制器，对应《接口文档》API-015。
 *
 * @author a
 */
@RestController
@RequestMapping("/api/v1/permissions")
@PreAuthorize("hasAuthority('user:manage')")
public class PermissionController {

    private final PermissionService permissionService;

    /**
     * 构造权限控制器。
     *
     * @param permissionService 权限服务
     */
    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /**
     * API-015 权限清单（标识/名称/类型）。
     *
     * @return 权限列表
     */
    @GetMapping
    public ApiResponse<List<PermissionVO>> list() {
        return ApiResponse.ok(permissionService.listPermissions());
    }
}
