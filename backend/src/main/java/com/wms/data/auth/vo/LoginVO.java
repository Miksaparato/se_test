package com.wms.data.auth.vo;

import com.wms.data.user.vo.UserVO;

/**
 * 登录响应体（API-001）。结构对应《接口文档》第 3 节示例：
 *
 * <pre>
 * {
 *   "token": "eyJhbGciOiJIUzI1NiIs...",
 *   "user": { "id": 1, "account": "admin", "name": "系统管理员", "roles": ["admin"] }
 * }
 * </pre>
 *
 * @param token          JWT 访问令牌
 * @param tokenType      令牌类型，固定 Bearer
 * @param expiresIn      有效期（秒）
 * @param user           登录用户信息（含角色与权限码，供前端菜单显隐使用）
 * @author a
 */
public record LoginVO(String token, String tokenType, long expiresIn, UserVO user) {

    /** 令牌类型固定值。 */
    public static final String BEARER = "Bearer";

    /**
     * 构造登录响应。
     *
     * @param token     JWT
     * @param expiresIn 有效期（秒）
     * @param user      用户信息
     * @return 登录响应
     */
    public static LoginVO of(String token, long expiresIn, UserVO user) {
        return new LoginVO(token, BEARER, expiresIn, user);
    }
}
