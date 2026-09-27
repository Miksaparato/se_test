package com.wms.security;

import com.wms.config.WmsProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * JWT 签发与解析，对应《接口文档》1.1「登录后返回 Token，请求头携带
 * {@code Authorization: Bearer <token>}」。
 *
 * <p>载荷约定（a 与 b、c 的冻结契约，详见《鉴权中间件规范》）：
 * <table border="1">
 *   <caption>JWT 载荷</caption>
 *   <tr><th>键</th><th>类型</th><th>含义</th></tr>
 *   <tr><td>{@code sub}</td><td>String</td><td>登录账号</td></tr>
 *   <tr><td>{@code uid}</td><td>Long</td><td>用户 id</td></tr>
 *   <tr><td>{@code name}</td><td>String</td><td>姓名/昵称</td></tr>
 *   <tr><td>{@code roles}</td><td>List&lt;String&gt;</td><td>角色标识</td></tr>
 *   <tr><td>{@code perms}</td><td>List&lt;String&gt;</td><td>权限码（《接口文档》1.4 清单）</td></tr>
 * </table>
 *
 * <p>算法固定 HS256；密钥长度不足 32 字节时 {@link Keys#hmacShaKeyFor} 会直接抛错，
 * 避免误用弱密钥。
 *
 * @author a
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    /** 用户 id 声明键。 */
    public static final String CLAIM_USER_ID = "uid";

    /** 姓名声明键。 */
    public static final String CLAIM_NAME = "name";

    /** 角色声明键。 */
    public static final String CLAIM_ROLES = "roles";

    /** 权限码声明键。 */
    public static final String CLAIM_PERMISSIONS = "perms";

    /** 最小密钥字节数（HS256 要求）。 */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey secretKey;

    private final long tokenExpireSeconds;

    /**
     * 构造 Token 提供者。
     *
     * @param properties 安全配置
     */
    public JwtTokenProvider(WmsProperties properties) {
        String secret = properties.getSecurity().getJwtSecret();
        byte[] keyBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "wms.security.jwt-secret 长度不足 " + MIN_SECRET_BYTES + " 字节，无法用于 HS256 签名");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.tokenExpireSeconds = properties.getSecurity().getTokenExpireSeconds();
    }

    /**
     * 签发 Token。
     *
     * @param loginUser 登录用户
     * @return 有效期内的 JWT 字符串
     */
    public String createToken(LoginUser loginUser) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(tokenExpireSeconds);
        return Jwts.builder()
                .subject(loginUser.getAccount())
                .claim(CLAIM_USER_ID, loginUser.getUserId())
                .claim(CLAIM_NAME, loginUser.getName())
                .claim(CLAIM_ROLES, List.copyOf(loginUser.getRoles()))
                .claim(CLAIM_PERMISSIONS, List.copyOf(loginUser.getPermissions()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并校验 Token。
     *
     * @param token JWT 字符串
     * @return Token 载荷
     * @throws JwtException Token 非法、签名不匹配或已过期
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 判断 Token 是否合法（签名有效且未过期）。
     *
     * @param token JWT 字符串
     * @return 合法返回 true
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Token 校验失败：{}", ex.getMessage());
            return false;
        }
    }

    /**
     * 由 Token 载荷还原登录用户。
     *
     * @param claims Token 载荷
     * @return 登录用户，载荷缺少必要字段时返回 null
     */
    public LoginUser toLoginUser(Claims claims) {
        Object userIdClaim = claims.get(CLAIM_USER_ID);
        String account = claims.getSubject();
        if (userIdClaim == null || account == null) {
            return null;
        }
        Long userId = ((Number) userIdClaim).longValue();
        String name = claims.get(CLAIM_NAME, String.class);
        Set<String> roles = toStringSet(claims.get(CLAIM_ROLES));
        Set<String> permissions = toStringSet(claims.get(CLAIM_PERMISSIONS));
        return new LoginUser(userId, account, name, roles, permissions);
    }

    /**
     * 获取 Token 有效期（秒）。
     *
     * @return 有效期秒数
     */
    public long getTokenExpireSeconds() {
        return tokenExpireSeconds;
    }

    /**
     * 将声明值安全转换为不重复的字符串集合。
     *
     * @param claimValue 声明值（预期为 List&lt;String&gt;）
     * @return 字符串集合，类型不符时返回空集合
     */
    private Set<String> toStringSet(Object claimValue) {
        if (claimValue instanceof List<?> list) {
            Set<String> result = new LinkedHashSet<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return Set.of();
    }
}
