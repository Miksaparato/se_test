package com.wms.data.permission;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wms.domain.entity.Permission;
import com.wms.domain.entity.Role;
import com.wms.domain.entity.RolePermission;
import com.wms.domain.entity.UserRole;
import com.wms.domain.mapper.PermissionMapper;
import com.wms.domain.mapper.RoleMapper;
import com.wms.domain.mapper.RolePermissionMapper;
import com.wms.domain.mapper.UserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限装载服务：按用户装载角色与权限码。
 *
 * <p>这是 a 的 RBAC 数据底座，供认证（A-B6）与角色权限管理（A-B7）共用。
 * 查询采用「批量取数 + 内存组装」，禁止循环单条查询（《代码规范》4.4 / NFR-1）。
 *
 * @author a
 */
@Service
public class PermissionLoader {

    private final UserRoleMapper userRoleMapper;

    private final RoleMapper roleMapper;

    private final RolePermissionMapper rolePermissionMapper;

    private final PermissionMapper permissionMapper;

    /**
     * 构造权限装载服务。
     *
     * @param userRoleMapper       用户-角色关联 Mapper
     * @param roleMapper           角色 Mapper
     * @param rolePermissionMapper 角色-权限关联 Mapper
     * @param permissionMapper     权限 Mapper
     */
    public PermissionLoader(UserRoleMapper userRoleMapper,
                            RoleMapper roleMapper,
                            RolePermissionMapper rolePermissionMapper,
                            PermissionMapper permissionMapper) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
    }

    /**
     * 装载单个用户的角色标识与权限码。
     *
     * @param userId 用户 id
     * @return 角色与权限集合
     */
    public UserAuthority load(Long userId) {
        List<Long> roleIds = findRoleIds(userId);
        return new UserAuthority(findRoleCodesByIds(roleIds), findPermissionCodes(roleIds));
    }

    /**
     * 批量装载多个用户的角色标识（用于用户列表，避免 N+1）。
     *
     * @param userIds 用户 id 列表
     * @return userId → 角色标识列表
     */
    public Map<Long, List<String>> loadRoleCodes(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UserRole> relations = userRoleMapper.selectList(
                Wrappers.<UserRole>lambdaQuery().in(UserRole::getUserId, userIds));
        if (relations.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> roleCodeById = findRoleCodes(relations.stream()
                .map(UserRole::getRoleId).distinct().toList()).stream()
                .collect(Collectors.toMap(RoleCode::id, RoleCode::code));
        return relations.stream().collect(Collectors.groupingBy(
                UserRole::getUserId,
                Collectors.mapping(relation -> roleCodeById.get(relation.getRoleId()),
                        Collectors.filtering(java.util.Objects::nonNull, Collectors.toList()))));
    }

    /**
     * 查询用户的角色 id 列表。
     *
     * @param userId 用户 id
     * @return 角色 id 列表
     */
    public List<Long> findRoleIds(Long userId) {
        return userRoleMapper.selectList(
                        Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, userId))
                .stream().map(UserRole::getRoleId).distinct().toList();
    }

    /**
     * 查询角色 id 对应的角色标识（按 id 升序，保证输出稳定）。
     *
     * @param roleIds 角色 id 列表
     * @return 角色标识列表
     */
    public List<String> findRoleCodesByIds(List<Long> roleIds) {
        return findRoleCodes(roleIds).stream().map(RoleCode::code).toList();
    }

    /**
     * 查询角色 id 对应的权限码（去重、按权限 id 稳定排序）。
     *
     * @param roleIds 角色 id 列表
     * @return 权限码列表
     */
    public List<String> findPermissionCodes(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> permissionIds = rolePermissionMapper.selectList(
                        Wrappers.<RolePermission>lambdaQuery()
                                .in(RolePermission::getRoleId, roleIds))
                .stream().map(RolePermission::getPermissionId).distinct().sorted().toList();
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionMapper.selectBatchIds(permissionIds).stream()
                .sorted(java.util.Comparator.comparing(Permission::getId))
                .map(Permission::getCode)
                .toList();
    }

    /**
     * 查询角色标识（内部结构，避免重复解析）。
     *
     * @param roleIds 角色 id 列表
     * @return 角色 id 与标识
     */
    private List<RoleCode> findRoleCodes(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(roleIds).stream()
                .sorted(java.util.Comparator.comparing(Role::getId))
                .map(role -> new RoleCode(role.getId(), role.getCode()))
                .toList();
    }

    /**
     * 用户的角色与权限集合。
     *
     * @param roles       角色标识列表（有序）
     * @param permissions 权限码列表（有序）
     */
    public record UserAuthority(List<String> roles, List<String> permissions) {

        /**
         * 角色集合。
         *
         * @return 角色标识集合
         */
        public Set<String> roleSet() {
            return new LinkedHashSet<>(roles);
        }

        /**
         * 权限集合。
         *
         * @return 权限码集合
         */
        public Set<String> permissionSet() {
            return new LinkedHashSet<>(permissions);
        }

        /**
         * 空权限。
         *
         * @return 无角色无权限
         */
        public static UserAuthority empty() {
            return new UserAuthority(new ArrayList<>(), new ArrayList<>());
        }
    }

    /**
     * 角色 id 与标识的映射项。
     *
     * @param id   角色 id
     * @param code 角色标识
     */
    private record RoleCode(Long id, String code) {
    }
}
