package com.wms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 鉴权与安全相关配置，绑定 {@code application.yml} 中的 {@code wms.*} 节点。
 *
 * <p>负责人：a（A-B8 / COM-4）。
 *
 * @author a
 */
@Component
@ConfigurationProperties(prefix = "wms")
public class WmsProperties {

    /** 安全配置。 */
    private Security security = new Security();

    /** 跨域配置。 */
    private Cors cors = new Cors();

    /**
     * 获取安全配置。
     *
     * @return 安全配置
     */
    public Security getSecurity() {
        return security;
    }

    /**
     * 设置安全配置。
     *
     * @param security 安全配置
     */
    public void setSecurity(Security security) {
        this.security = security;
    }

    /**
     * 获取跨域配置。
     *
     * @return 跨域配置
     */
    public Cors getCors() {
        return cors;
    }

    /**
     * 设置跨域配置。
     *
     * @param cors 跨域配置
     */
    public void setCors(Cors cors) {
        this.cors = cors;
    }

    /** 安全配置项。 */
    public static class Security {

        /** JWT 签名密钥（HS256 要求 ≥ 32 字节）。 */
        private String jwtSecret = "wms-sim-local-dev-secret-key-please-change-in-production";

        /** Token 有效期（秒），默认 8 小时。 */
        private long tokenExpireSeconds = 28800L;

        /** 免鉴权路径（白名单）。 */
        private String loginPath = "/api/v1/auth/login";

        /**
         * 获取 JWT 密钥。
         *
         * @return 密钥
         */
        public String getJwtSecret() {
            return jwtSecret;
        }

        /**
         * 设置 JWT 密钥。
         *
         * @param jwtSecret 密钥
         */
        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
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
         * 设置 Token 有效期（秒）。
         *
         * @param tokenExpireSeconds 有效期秒数
         */
        public void setTokenExpireSeconds(long tokenExpireSeconds) {
            this.tokenExpireSeconds = tokenExpireSeconds;
        }

        /**
         * 获取免鉴权登录路径。
         *
         * @return 登录路径
         */
        public String getLoginPath() {
            return loginPath;
        }

        /**
         * 设置免鉴权登录路径。
         *
         * @param loginPath 登录路径
         */
        public void setLoginPath(String loginPath) {
            this.loginPath = loginPath;
        }
    }

    /** 跨域配置项。 */
    public static class Cors {

        /** 允许的前端来源。 */
        private List<String> allowedOrigins = new ArrayList<>(List.of(
                "http://localhost:5173", "http://127.0.0.1:5173"));

        /**
         * 获取允许的来源列表。
         *
         * @return 来源列表
         */
        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        /**
         * 设置允许的来源列表。
         *
         * @param allowedOrigins 来源列表
         */
        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }
}
