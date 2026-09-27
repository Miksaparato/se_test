package com.wms.data.permission.vo;

import com.wms.domain.entity.Permission;

/**
 * 权限视图对象（API-015）。
 *
 * @param id     权限 id
 * @param code   权限码，如 user:manage
 * @param name   权限名称
 * @param type   权限类型：menu 菜单 / action 操作
 * @param sortNo 展示排序
 * @param remark 备注
 * @author a
 */
public record PermissionVO(Long id, String code, String name, String type, Integer sortNo, String remark) {

    /**
     * 由实体构造。
     *
     * @param permission 权限实体
     * @return 权限视图对象
     */
    public static PermissionVO from(Permission permission) {
        return new PermissionVO(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getType(),
                permission.getSortNo(),
                permission.getRemark());
    }
}
