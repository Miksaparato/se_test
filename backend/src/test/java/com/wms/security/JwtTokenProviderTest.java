package com.wms.security;

import com.wms.config.WmsProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JwtTokenProvider} 单元测试（a，A-B8 / T-3）。
 *
 * <p>纯 JUnit 5，不启动 Spring 上下文（《代码规范》4.7）。
 *
 * @author a
 */
class JwtTokenProviderTest {

    private static final String SECRET = "wms-sim-unit-test-secret-key-0123456789abcdef";

    private JwtTokenProvider tokenProvider;

    /**
     * 构造被测对象。
     */
    @BeforeEach
    void setUp() {
        WmsProperties properties = new WmsProperties();
        properties.getSecurity().setJwtSecret(SECRET);
        properties.getSecurity().setTokenExpireSeconds(3600L);
        tokenProvider = new JwtTokenProvider(properties);
    }

    /**
     * 构造登录用户。
     *
     * @param permissions 权限码
     * @return 登录用户
     */
    private LoginUser loginUser(Set<String> permissions) {
        return new LoginUser(7L, "operator", "仓库操作员", Set.of("operator"), permissions);
    }

    @Test
    @DisplayName("签发的 Token 应能完整还原用户 id/账号/姓名/角色/权限")
    void should_round_trip_login_user() {
        LoginUser source = loginUser(Set.of("sku:manage", "recommend:view"));

        LoginUser restored = tokenProvider.toLoginUser(
                tokenProvider.parseClaims(tokenProvider.createToken(source)));

        assertThat(restored).isNotNull();
        assertThat(restored.getUserId()).isEqualTo(7L);
        assertThat(restored.getAccount()).isEqualTo("operator");
        assertThat(restored.getName()).isEqualTo("仓库操作员");
        assertThat(restored.getRoles()).containsExactly("operator");
        assertThat(restored.getPermissions()).containsExactlyInAnyOrder("sku:manage", "recommend:view");
    }

    @Test
    @DisplayName("权限码应转换为 Spring Security 的 GrantedAuthority，供 @PreAuthorize 使用")
    void should_expose_permissions_as_authorities() {
        LoginUser restored = tokenProvider.toLoginUser(
                tokenProvider.parseClaims(tokenProvider.createToken(loginUser(Set.of("user:manage")))));

        assertThat(restored.getAuthorities()).extracting("authority").containsExactly("user:manage");
        assertThat(restored.hasPermission("user:manage")).isTrue();
        assertThat(restored.hasPermission("config:manage")).isFalse();
        assertThat(restored.hasPermission(null)).isFalse();
    }

    @Test
    @DisplayName("Token 有效期应与配置一致")
    void should_use_configured_expiry() {
        Claims claims = tokenProvider.parseClaims(tokenProvider.createToken(loginUser(Set.of("sim:view"))));

        long seconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(seconds).isEqualTo(3600L);
        assertThat(tokenProvider.getTokenExpireSeconds()).isEqualTo(3600L);
        assertThat(claims.getSubject()).isEqualTo("operator");
    }

    @Test
    @DisplayName("合法 Token 应通过校验")
    void should_validate_valid_token() {
        String token = tokenProvider.createToken(loginUser(Set.of("sim:view")));

        assertThat(tokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("已被篡改/换密钥签名的 Token 必须校验失败")
    void should_reject_token_signed_by_other_key() {
        String forged = Jwts.builder()
                .subject("hacker")
                .claim(JwtTokenProvider.CLAIM_USER_ID, 999L)
                .claim(JwtTokenProvider.CLAIM_PERMISSIONS, java.util.List.of("user:manage"))
                .signWith(Keys.hmacShaKeyFor("another-secret-key-long-enough-000000000000".getBytes(
                        StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        assertThat(tokenProvider.validateToken(forged)).isFalse();
        assertThatThrownBy(() -> tokenProvider.parseClaims(forged))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    @DisplayName("已过期 Token 必须校验失败")
    void should_reject_expired_token() {
        String expired = Jwts.builder()
                .subject("operator")
                .claim(JwtTokenProvider.CLAIM_USER_ID, 7L)
                .issuedAt(new Date(System.currentTimeMillis() - 7200_000L))
                .expiration(new Date(System.currentTimeMillis() - 3600_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        assertThat(tokenProvider.validateToken(expired)).isFalse();
    }

    @Test
    @DisplayName("空值与非法字符串应校验失败且不抛异常")
    void should_reject_blank_or_malformed_token() {
        assertThat(tokenProvider.validateToken(null)).isFalse();
        assertThat(tokenProvider.validateToken("")).isFalse();
        assertThat(tokenProvider.validateToken("   ")).isFalse();
        assertThat(tokenProvider.validateToken("abc.def.ghi")).isFalse();
    }

    @Test
    @DisplayName("载荷缺少用户 id 时应返回 null，避免构造出半残登录态")
    void should_return_null_when_user_id_claim_absent() {
        String token = Jwts.builder()
                .subject("operator")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        assertThat(tokenProvider.toLoginUser(tokenProvider.parseClaims(token))).isNull();
    }

    @Test
    @DisplayName("密钥长度不足 32 字节时应拒绝启动，避免弱密钥")
    void should_fail_fast_on_short_secret() {
        WmsProperties weak = new WmsProperties();
        weak.getSecurity().setJwtSecret("too-short");

        assertThatThrownBy(() -> new JwtTokenProvider(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt-secret");
    }
}
