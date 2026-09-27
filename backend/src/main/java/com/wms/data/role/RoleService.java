package com.wms.data.role;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.data.permission.PermissionLoader;
import com.wms.data.permission.PermissionService;
import com.wms.data.role.dto.CreateRoleRequest;
import com.wms.data.role.dto.UpdateRoleRequest;
import com.wms.data.role.vo.RoleVO;
import com.wms.domain.entity.Permission;
import com.wms.domain.entity.Role;
import com.wms.domain.entity.RolePermission;
import com.wms.domain.entity.UserRole;
import com.wms.domain.mapper.PermissionMapper;
import com.wms.domain.mapper.RoleMapper;
import com.wms.domain.mapper.RolePermissionMapper;
import com.wms.domain.mapper.UserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色服务（A-B7）：角色 CRUD 与权限分配。
 *
 * <p>三条硬约束，对应《需求文档》FR-RBAC-2 / FR-RBAC-4：
 * <ol>
 *   <li>内置四角色（{@code is_builtin = 1}）**禁止删除**，但权限可被管理员调整；</li>
 *   <li>仍被用户引用的角色**禁止删除**，避免用户失去全部权限；</li>
 *   <li>角色标识 {@code code} 是权限矩阵与前端路由的判断依据，**不允许改**，且全局唯一。</li>
 * </ol>
 *
 * @author a
 */
@Service
public class RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    private final RoleMapper roleMapper;

    private final RolePermissionMapper rolePermissionMapper;

    private final UserRoleMapper userRoleMapper;

    private final PermissionMapper permissionMapper;

    private final PermissionLoader permissionLoader;

    private final PermissionService permissionService;

    /**
     * 构造角色服务。
     *
     * @param roleMapper           角色 Mapper
     * @param rolePermissionMapper 角色-权限关联 Mapper
     * @param userRoleMapper       用户-角色关联 Mapper
     * @param permissionMapper     权限 Mapper
     * @param permissionLoader     权限装载服务
     * @param permissionService    权限服务（权限码校验）
     */
    public RoleService(RoleMapper roleMapper,
                       RolePermissionMapper rolePermissionMapper,
                       UserRoleMapper userRoleMapper,
                       PermissionMapper permissionMapper,
                       PermissionLoader permissionLoader,
                       PermissionService permissionService) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.permissionMapper = permissionMapper;
        this.permissionLoader = permissionLoader;
        this.permissionService = permissionService;
    }

    /**
     * 角色列表（API-010），含内置四角色、关联用户数与权限码。
     *
     * @return 角色列表
     */
    public List<RoleVO> listRoles() {
        List<Role> roles = roleMapper.selectList(Wrappers.<Role>lambdaQuery().orderByAsc(Role::getId));
        if (roles.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = roles.stream().map(Role::getId).toList();

        Map<Long, Long> userCountByRole = countUsersByRole(roleIds);
        Map<Long, List<String>> permissionsByRole = loadPermissionCodesByRole(roleIds);

        return roles.stream()
                .map(role -> RoleVO.from(role,
                        userCountByRole.getOrDefault(role.getId(), 0L),
                        permissionsByRole.getOrDefault(role.getId(), List.of())))
                .toList();
    }

    /**
     * 角色详情，不存在时抛 40408。
     *
     * @param roleId 角色 id
     * @return 角色视图对象（含权限码与用户数）
     */
    public RoleVO getRole(Long roleId) {
        Role role = getByIdOrThrow(roleId);
        return RoleVO.from(role,
                countUsersByRole(List.of(roleId)).getOrDefault(roleId, 0L),
                loadPermissionCodesByRole(List.of(roleId)).getOrDefault(roleId, List.of()));
    }

    /**
     * 创建角色（API-011）。
     *
     * @param request 创建请求
     * @return 新建角色视图对象
     */
    @Transactional(rollbackFor = Exception.class)
    public RoleVO createRole(CreateRoleRequest request) {
        if (findByCode(request.code()) != null) {
            throw new BizException(ErrorCode.ROLE_CODE_DUPLICATE);
        }
        Role entity = new Role();
        entity.setCode(request.code().trim());
        entity.setName(request.name().trim());
        entity.setDescription(request.description());
        entity.setIsBuiltin(0);
        roleMapper.insert(entity);
        log.info("创建角色成功 code={} name={}", entity.getCode(), entity.getName());
        return RoleVO.from(entity, 0L, List.of());
    }

    /**
     * 更新角色名称/描述（API-012）。角色标识不允许修改。
     *
     * @param roleId  角色 id
     * @param request 更新请求
     * @return 更新后的角色视图对象
     */
    @Transactional(rollbackFor = Exception.class)
    public RoleVO updateRole(Long roleId, UpdateRoleRequest request) {
        getByIdOrThrow(roleId);
        Role update = new Role();
        update.setId(roleId);
        update.setName(request.name());
        update.setDescription(request.description());
        roleMapper.updateById(update);
        log.info("更新角色成功 roleId={}", roleId);
        return getRole(roleId);
    }

    /**
     * 删除角色（API-013）。
     *
     * @param roleId 角色 id
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long roleId) {
        Role role = getByIdOrThrow(roleId);
        if (role.getIsBuiltin() != null && role.getIsBuiltin() == 1) {
            throw new BizException(ErrorCode.BUILTIN_ROLE_NOT_DELETABLE, "内置角色不允许删除：" + role.getName());
        }
        long userCount = countUsersByRole(List.of(roleId)).getOrDefault(roleId, 0L);
        if (userCount > 0) {
            throw new BizException(ErrorCode.ROLE_IN_USE,
                    "该角色仍被 " + userCount + " 个用户使用，无法删除");
        }
        rolePermissionMapper.delete(Wrappers.<RolePermission>lambdaQuery()
                .eq(RolePermission::getRoleId, roleId));
        roleMapper.deleteById(roleId);
        log.info("删除角色成功 roleId={} code={}", roleId, role.getCode());
    }

    /**
     * 为角色分配权限（API-014），**全量覆盖**语义：先删后插，单事务完成。
     *
     * @param roleId  角色 id
     * @param codes   权限码列表，空数组表示收回全部权限
     * @return 更新后的角色视图对象
     */
    @Transactional(rollbackFor = Exception.class)
    public RoleVO assignPermissions(Long roleId, List<String> codes) {
        Role role = getByIdOrThrow(roleId);
        List<String> target = codes == null ? List.of() : codes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        permissionService.validateCodes(target);

        Map<String, Long> permissionIdByCode = permissionService.loadPermissionIdByCode();
        rolePermissionMapper.delete(Wrappers.<RolePermission>lambdaQuery()
                .eq(RolePermission::getRoleId, roleId));
        for (String code : target) {
            RolePermission relation = new RolePermission();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionIdByCode.get(code));
            rolePermissionMapper.insert(relation);
        }
        log.info("分配角色权限成功 roleId={} code={} 权限数={} 权限码={}",
                roleId, role.getCode(), target.size(), target);
        return getRole(roleId);
    }

    /**
     * 查询角色的权限码列表。
     *
     * @param roleId 角色 id
     * @return 权限码列表
     */
    public List<String> permissionCodesOf(Long roleId) {
        return loadPermissionCodesByRole(List.of(roleId)).getOrDefault(roleId, List.of());
    }

    /**
     * 角色标识 → 角色 id 映射（按 id 升序，输出稳定）。
     *
     * @return 映射
     */
    public Map<String, Long> loadRoleIdByCode() {
        Map<String, Long> mapping = new java.util.LinkedHashMap<>();
        for (Role role : roleMapper.selectList(Wrappers.<Role>lambdaQuery().orderByAsc(Role::getId))) {
            mapping.put(role.getCode(), role.getId());
        }
        return mapping;
    }

    /**
     * 按角色标识查询角色。
     *
     * @param code 角色标识
     * @return 角色实体，不存在时返回 null
     */
    public Role findByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return roleMapper.selectOne(Wrappers.<Role>lambdaQuery().eq(Role::getCode, code));
    }

    /**
     * 按角色标识查询角色，不存在时抛 40408。
     *
     * @param code 角色标识
     * @return 角色实体
     */
    public Role getByCodeOrThrow(String code) {
        Role role = findByCode(code);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND, "角色不存在：" + code);
        }
        return role;
    }

    /**
     * 按 id 查询角色，不存在时抛 40408。
     *
     * @param roleId 角色 id
     * @return 角色实体
     */
    public Role getByIdOrThrow(Long roleId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        return role;
    }

    /**
     * 统计各角色的关联用户数（一次查询，避免 N+1）。
     *
     * @param roleIds 角色 id 列表
     * @return roleId → 用户数
     */
    private Map<Long, Long> countUsersByRole(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<UserRole> wrapper = Wrappers.<UserRole>lambdaQuery()
                .in(UserRole::getRoleId, roleIds);
        Map<Long, Long> counts = new HashMap<>();
        for (UserRole relation : userRoleMapper.selectList(wrapper)) {
            counts.merge(relation.getRoleId(), 1L, Long::sum);
        }
        return counts;
    }

    /**
     * 批量装载各角色的权限码（一次查询，避免 N+1）。
     *
     * @param roleIds 角色 id 列表
     * @return roleId → 权限码列表
     */
    private Map<Long, List<String>> loadPermissionCodesByRole(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Map.of();
        }
        List<RolePermission> relations = rolePermissionMapper.selectList(
                Wrappers.<RolePermission>lambdaQuery().in(RolePermission::getRoleId, roleIds));
        if (relations.isEmpty()) {
            return Map.of();
        }
        List<Long> permissionIds = relations.stream()
                .map(RolePermission::getPermissionId).distinct().toList();
        Map<Long, String> codeById = new HashMap<>();
        for (Permission permission : permissionMapper.selectBatchIds(permissionIds)) {
            codeById.put(permission.getId(), permission.getCode());
        }
        Map<Long, List<String>> result = new HashMap<>();
        for (RolePermission relation : relations) {
            String code = codeById.get(relation.getPermissionId());
            if (code != null) {
                result.computeIfAbsent(relation.getRoleId(), key -> new java.util.ArrayList<>()).add(code);
            }
        }
        result.replaceAll((key, codes) -> codes.stream().sorted(Comparator.naturalOrder()).toList());
        return result;
    }
}
