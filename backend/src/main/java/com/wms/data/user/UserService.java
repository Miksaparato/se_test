package com.wms.data.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.common.PageResult;
import com.wms.data.permission.PermissionLoader;
import com.wms.data.role.RoleService;
import com.wms.data.user.dto.CreateUserRequest;
import com.wms.data.user.dto.UpdateUserRequest;
import com.wms.data.user.vo.UserItemVO;
import com.wms.data.user.vo.UserVO;
import com.wms.domain.entity.User;
import com.wms.domain.entity.UserRole;
import com.wms.domain.mapper.UserMapper;
import com.wms.domain.mapper.UserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户服务：用户查询、创建、更新、启停、删除与改密。
 *
 * <p>对应 A-B6（API-003/004/005）与 A-B7（API-006~009）。
 * 密码一律经 {@link PasswordEncoder}（BCrypt）加密后存储，任何方法都**不得返回密码字段**（NFR-6）。
 *
 * @author a
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /** 账号启用状态。 */
    public static final String STATUS_ACTIVE = "active";

    /** 账号禁用状态。 */
    public static final String STATUS_DISABLED = "disabled";

    /** 默认角色标识（未指定角色时赋予只读访客）。 */
    private static final String DEFAULT_ROLE_CODE = "viewer";

    /** 系统保留账号：不允许删除，避免把系统锁死。 */
    private static final String RESERVED_ACCOUNT = "admin";

    private final UserMapper userMapper;

    private final UserRoleMapper userRoleMapper;

    private final PermissionLoader permissionLoader;

    private final RoleService roleService;

    private final PasswordEncoder passwordEncoder;

    /**
     * 构造用户服务。
     *
     * @param userMapper       用户 Mapper
     * @param userRoleMapper   用户-角色关联 Mapper
     * @param permissionLoader 权限装载服务
     * @param roleService      角色服务
     * @param passwordEncoder  密码编码器
     */
    public UserService(UserMapper userMapper,
                       UserRoleMapper userRoleMapper,
                       PermissionLoader permissionLoader,
                       RoleService roleService,
                       PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.permissionLoader = permissionLoader;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户分页列表（API-006）。
     *
     * <p>先分页查用户，再批量查角色，避免逐用户查询造成 N+1（《代码规范》4.4）。
     *
     * @param page    页码，从 1 开始
     * @param pageSize 每页条数
     * @param status  账号状态过滤，可为 null
     * @param keyword 账号/姓名关键字，可为 null
     * @return 分页结果
     */
    public PageResult<UserItemVO> listUsers(Integer page, Integer pageSize, String status, String keyword) {
        int current = PageResult.normalizePage(page);
        int size = PageResult.normalizePageSize(pageSize);

        LambdaQueryWrapper<User> wrapper = Wrappers.<User>lambdaQuery()
                .eq(StringUtils.hasText(status), User::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(User::getAccount, keyword)
                        .or()
                        .like(User::getName, keyword))
                .orderByAsc(User::getId);

        Page<User> result = userMapper.selectPage(new Page<>(current, size), wrapper);
        List<Long> userIds = result.getRecords().stream().map(User::getId).toList();
        Map<Long, List<String>> rolesByUser = permissionLoader.loadRoleCodes(userIds);

        List<UserItemVO> items = result.getRecords().stream()
                .map(user -> UserItemVO.from(user, rolesByUser.getOrDefault(user.getId(), List.of())))
                .toList();
        return new PageResult<>(items, result.getTotal(), current, size);
    }

    /**
     * 按账号查询用户。
     *
     * @param account 登录账号
     * @return 用户实体，不存在时返回 null
     */
    public User findByAccount(String account) {
        if (account == null || account.isBlank()) {
            return null;
        }
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getAccount, account));
    }

    /**
     * 按 id 查询用户，不存在时抛 40407。
     *
     * @param userId 用户 id
     * @return 用户实体
     */
    public User getByIdOrThrow(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 校验密码是否匹配。
     *
     * @param rawPassword    明文密码
     * @param hashedPassword 数据库中的 BCrypt 哈希
     * @return 匹配返回 true
     */
    public boolean matchesPassword(String rawPassword, String hashedPassword) {
        return rawPassword != null && hashedPassword != null
                && passwordEncoder.matches(rawPassword, hashedPassword);
    }

    /**
     * 记录最近登录时间。
     *
     * @param userId 用户 id
     */
    public void touchLastLogin(Long userId) {
        User update = new User();
        update.setId(userId);
        update.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(update);
    }

    /**
     * 修改本人密码（API-004）：先校验原密码，再写入新密码哈希。
     *
     * @param userId      用户 id
     * @param oldPassword 原密码
     * @param newPassword 新密码
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = getByIdOrThrow(userId);
        if (!matchesPassword(oldPassword, user.getPassword())) {
            throw new BizException(ErrorCode.OLD_PASSWORD_INCORRECT);
        }
        if (matchesPassword(newPassword, user.getPassword())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "新密码不能与原密码相同");
        }
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
        log.info("用户修改密码成功 userId={} account={}", userId, user.getAccount());
    }

    /**
     * 创建用户（API-005）：账号唯一校验、密码加密、角色绑定。
     *
     * @param request    创建请求
     * @param operatorId 操作人 id（记录 created_by）
     * @return 新建用户视图对象（含角色与权限）
     */
    @Transactional(rollbackFor = Exception.class)
    public UserVO createUser(CreateUserRequest request, Long operatorId) {
        if (findByAccount(request.account()) != null) {
            throw new BizException(ErrorCode.ACCOUNT_DUPLICATE);
        }
        List<String> roleCodes = normalizeRoleCodes(request.roles());
        Map<String, Long> roleIdByCode = roleService.loadRoleIdByCode();
        validateRoleCodes(roleCodes, roleIdByCode);

        User entity = new User();
        entity.setAccount(request.account().trim());
        entity.setPassword(passwordEncoder.encode(request.password()));
        entity.setName(request.name());
        entity.setEmail(request.email());
        entity.setPhone(request.phone());
        entity.setStatus(request.status() == null ? STATUS_ACTIVE : request.status());
        entity.setRemark(request.remark());
        entity.setCreatedBy(operatorId);
        userMapper.insert(entity);

        replaceUserRoles(entity.getId(), roleCodes, roleIdByCode);

        log.info("创建用户成功 account={} roles={} 操作人={}", entity.getAccount(), roleCodes, operatorId);
        return toVO(entity);
    }

    /**
     * 更新用户（API-007）：仅姓名/邮箱/手机号/备注/角色。
     *
     * @param userId     目标用户 id
     * @param request    更新请求
     * @param operatorId 操作人 id
     * @return 更新后的用户视图对象
     */
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateUser(Long userId, UpdateUserRequest request, Long operatorId) {
        User user = getByIdOrThrow(userId);

        User update = new User();
        update.setId(userId);
        update.setName(request.name());
        update.setEmail(request.email());
        update.setPhone(request.phone());
        update.setRemark(request.remark());
        userMapper.updateById(update);

        if (request.roles() != null) {
            List<String> roleCodes = request.roles().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
            Map<String, Long> roleIdByCode = roleService.loadRoleIdByCode();
            validateRoleCodes(roleCodes, roleIdByCode);
            replaceUserRoles(userId, roleCodes, roleIdByCode);
        }

        log.info("更新用户成功 userId={} 操作人={} 角色已更新={}", userId, operatorId, request.roles() != null);
        return toVO(getByIdOrThrow(userId));
    }

    /**
     * 启用/禁用用户（API-008）。
     *
     * <p>禁止禁用当前登录账号，避免管理员把自己锁在系统外。
     *
     * @param userId     目标用户 id
     * @param status     目标状态：active / disabled
     * @param operatorId 操作人 id
     * @return 更新后的用户视图对象
     */
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateStatus(Long userId, String status, Long operatorId) {
        User user = getByIdOrThrow(userId);
        if (STATUS_DISABLED.equals(status) && userId.equals(operatorId)) {
            throw new BizException(ErrorCode.SELF_OPERATION_FORBIDDEN, "不允许禁用当前登录账号");
        }
        User update = new User();
        update.setId(userId);
        update.setStatus(status);
        userMapper.updateById(update);
        log.info("更新用户状态 userId={} account={} status={} 操作人={}",
                userId, user.getAccount(), status, operatorId);
        return toVO(getByIdOrThrow(userId));
    }

    /**
     * 删除用户（API-009）：连同用户-角色关联一并清理。
     *
     * <p>保护规则：保留账号 {@code admin} 不允许删除；不允许删除当前登录账号。
     *
     * @param userId     目标用户 id
     * @param operatorId 操作人 id
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId, Long operatorId) {
        User user = getByIdOrThrow(userId);
        if (userId.equals(operatorId)) {
            throw new BizException(ErrorCode.SELF_OPERATION_FORBIDDEN, "不允许删除当前登录账号");
        }
        if (RESERVED_ACCOUNT.equals(user.getAccount())) {
            throw new BizException(ErrorCode.RESERVED_ACCOUNT_NOT_DELETABLE);
        }
        userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, userId));
        userMapper.deleteById(userId);
        log.info("删除用户 userId={} account={} 操作人={}", userId, user.getAccount(), operatorId);
    }

    /**
     * 组装用户视图对象（含角色与权限码）。
     *
     * @param user 用户实体
     * @return 用户视图对象
     */
    public UserVO toVO(User user) {
        PermissionLoader.UserAuthority authority = permissionLoader.load(user.getId());
        return UserVO.from(user, authority.roles(), authority.permissions());
    }

    /**
     * 覆盖式设置用户角色：先删后插，单事务内完成。
     *
     * @param userId       用户 id
     * @param roleCodes    角色标识列表
     * @param roleIdByCode 角色标识 → id 映射
     */
    private void replaceUserRoles(Long userId, List<String> roleCodes, Map<String, Long> roleIdByCode) {
        userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, userId));
        for (String roleCode : roleCodes) {
            UserRole relation = new UserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleIdByCode.get(roleCode));
            userRoleMapper.insert(relation);
        }
    }

    /**
     * 校验角色标识均存在。
     *
     * @param roleCodes    角色标识列表
     * @param roleIdByCode 角色标识 → id 映射
     */
    private void validateRoleCodes(List<String> roleCodes, Map<String, Long> roleIdByCode) {
        for (String roleCode : roleCodes) {
            if (!roleIdByCode.containsKey(roleCode)) {
                throw new BizException(ErrorCode.ROLE_NOT_FOUND, "角色不存在：" + roleCode);
            }
        }
    }

    /**
     * 角色标识规整：去空、去重、缺省为 viewer。
     *
     * @param roles 原始角色标识列表
     * @return 规整后的角色标识列表
     */
    private List<String> normalizeRoleCodes(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of(DEFAULT_ROLE_CODE);
        }
        List<String> cleaned = roles.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        return cleaned.isEmpty() ? List.of(DEFAULT_ROLE_CODE) : cleaned;
    }
}
