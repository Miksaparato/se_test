package com.wms.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 当前登录用户主体，由 {@link JwtAuthFilter} 从 Token 还原并放入 SecurityContext。
 *
 * <p>业务 Controller 通过 {@code @AuthenticationPrincipal LoginUser loginUser} 取用。
 * 权限码集合直接作为 {@link GrantedAuthority}，供 {@code @PreAuthorize("hasAuthority('xxx')")} 校验
 * （《代码规范》4.3）。
 *
 * <p>密码字段恒为 null：Token 中不携带密码，本对象也永不返回给前端（NFR-6）。
 *
 * @author a
 */
public class LoginUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    /** 用户 id。 */
    private final Long userId;

    /** 登录账号。 */
    private final String account;

    /** 姓名/昵称。 */
    private final String name;

    /** 角色标识集合，如 {@code ["admin"]}。 */
    private final Set<String> roles;

    /** 权限码集合，如 {@code ["user:manage"]}。 */
    private final Set<String> permissions;

    /** Spring Security 权限集合。 */
    private final List<GrantedAuthority> authorities;

    /**
     * 构造登录用户主体。
     *
     * @param userId      用户 id
     * @param account     登录账号
     * @param name        姓名/昵称
     * @param roles       角色标识集合
     * @param permissions 权限码集合
     */
    public LoginUser(Long userId, String account, String name, Set<String> roles, Set<String> permissions) {
        this.userId = userId;
        this.account = account;
        this.name = name;
        this.roles = roles == null ? Set.of() : Set.copyOf(roles);
        this.permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        this.authorities = this.permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    /**
     * 获取用户 id。
     *
     * @return 用户 id
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 获取登录账号。
     *
     * @return 登录账号
     */
    public String getAccount() {
        return account;
    }

    /**
     * 获取姓名/昵称。
     *
     * @return 姓名
     */
    public String getName() {
        return name;
    }

    /**
     * 获取角色标识集合。
     *
     * @return 角色标识集合
     */
    public Set<String> getRoles() {
        return roles;
    }

    /**
     * 获取权限码集合。
     *
     * @return 权限码集合
     */
    public Set<String> getPermissions() {
        return permissions;
    }

    /**
     * 判断是否拥有某权限码（仅供业务代码做展示性判断，接口鉴权一律走 {@code @PreAuthorize}）。
     *
     * @param permissionCode 权限码
     * @return 拥有返回 true
     */
    public boolean hasPermission(String permissionCode) {
        return permissionCode != null && permissions.contains(permissionCode);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return account;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
