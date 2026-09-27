package com.wms.common;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码，对应《代码规范》4.2 错误码表与《接口文档》1.3 HTTP 状态码表。
 *
 * <p>错误码构成为 5 位数字：前 3 位等于对应的 HTTP 状态码（400/401/403/404/422/500），
 * 后 2 位为同类错误的序号。因此 {@link #toHttpStatus(int)} 可直接由错误码反推 HTTP 状态码。
 *
 * <table border="1">
 *   <caption>错误码段</caption>
 *   <tr><th>错误码段</th><th>HTTP</th><th>含义</th></tr>
 *   <tr><td>400xx</td><td>400</td><td>参数校验失败</td></tr>
 *   <tr><td>401xx</td><td>401</td><td>未认证</td></tr>
 *   <tr><td>403xx</td><td>403</td><td>越权</td></tr>
 *   <tr><td>404xx</td><td>404</td><td>资源不存在</td></tr>
 *   <tr><td>422xx</td><td>422</td><td>业务规则校验失败</td></tr>
 *   <tr><td>500xx</td><td>500</td><td>系统异常</td></tr>
 * </table>
 *
 * @author a
 */
public enum ErrorCode {

    // ---------- 400 参数校验失败 ----------
    /** 请求参数校验失败。 */
    PARAM_INVALID(40001, "参数校验失败"),
    /** 缺少必要的请求头（如 Authorization）。 */
    PARAM_MISSING_HEADER(40002, "缺少必要的请求头"),

    // ---------- 401 未认证 ----------
    /** 未登录或 Token 失效。 */
    UNAUTHORIZED(40101, "未登录或登录已失效，请重新登录"),
    /** 账号或密码错误。 */
    LOGIN_FAILED(40102, "账号或密码错误"),
    /** 账号已被禁用。 */
    ACCOUNT_DISABLED(40103, "账号已被禁用，请联系管理员"),
    /** 原密码错误。 */
    OLD_PASSWORD_INCORRECT(40104, "原密码错误"),

    // ---------- 403 越权 ----------
    /** 已登录但无该操作权限。 */
    FORBIDDEN(40301, "无该操作权限"),
    /** 内置角色不允许删除。 */
    BUILTIN_ROLE_NOT_DELETABLE(40302, "内置角色不允许删除"),

    // ---------- 404 资源不存在 ----------
    /** 通用资源不存在。 */
    NOT_FOUND(40401, "资源不存在"),
    /** 仓库不存在。 */
    WAREHOUSE_NOT_FOUND(40402, "仓库不存在"),
    /** 货架不存在。 */
    RACK_NOT_FOUND(40403, "货架不存在"),
    /** 库位不存在。 */
    LOCATION_NOT_FOUND(40404, "库位不存在"),
    /** 货物（SKU）不存在。 */
    SKU_NOT_FOUND(40405, "货物不存在"),
    /** 订单不存在。 */
    ORDER_NOT_FOUND(40406, "订单不存在"),
    /** 用户不存在。 */
    USER_NOT_FOUND(40407, "用户不存在"),
    /** 角色不存在。 */
    ROLE_NOT_FOUND(40408, "角色不存在"),
    /** 推荐结果不存在。 */
    RECOMMENDATION_NOT_FOUND(40409, "推荐结果不存在"),
    /** 分配方案不存在。 */
    PLAN_NOT_FOUND(40410, "分配方案不存在"),
    /** 仿真记录不存在。 */
    SIMULATION_NOT_FOUND(40411, "仿真记录不存在"),
    /** 请求路径不存在。 */
    API_NOT_FOUND(40412, "请求的接口不存在"),

    // ---------- 422 业务规则校验失败 ----------
    /** 库位已被占用。 */
    LOCATION_OCCUPIED(42201, "库位已占用"),
    /** 重货层高违规。 */
    WEIGHT_LAYER_VIOLATION(42202, "重货层高违规"),
    /** 货物尺寸超出库位容量。 */
    CAPACITY_EXCEEDED(42203, "货物尺寸超出库位容量"),
    /** 库位编码已存在。 */
    LOCATION_CODE_DUPLICATE(42204, "库位编码已存在"),
    /** 仓库编码已存在。 */
    WAREHOUSE_CODE_DUPLICATE(42205, "仓库编码已存在"),
    /** 货物编码已存在。 */
    SKU_CODE_DUPLICATE(42206, "货物编码已存在"),
    /** 账号已存在。 */
    ACCOUNT_DUPLICATE(42207, "账号已存在"),
    /** 角色标识已存在。 */
    ROLE_CODE_DUPLICATE(42208, "角色标识已存在"),
    /** 订单号已存在。 */
    ORDER_NO_DUPLICATE(42209, "订单号已存在"),
    /** 资源被引用，无法删除。 */
    RESOURCE_IN_USE(42210, "资源被引用，无法删除"),
    /** 评分权重之和必须为 1。 */
    WEIGHT_SUM_INVALID(42211, "权重之和必须为 1"),
    /** 权限码非法（不在《接口文档》1.4 清单内）。 */
    PERMISSION_CODE_INVALID(42212, "存在非法的权限码"),
    /** 库位状态不允许该操作。 */
    LOCATION_STATUS_INVALID(42213, "库位当前状态不允许该操作"),
    /** 角色仍被用户引用，无法删除。 */
    ROLE_IN_USE(42214, "角色仍被用户引用，无法删除"),
    /** 不允许对当前登录账号执行该操作（防自锁）。 */
    SELF_OPERATION_FORBIDDEN(42215, "不允许对当前登录账号执行该操作"),
    /** 系统保留账号不允许删除。 */
    RESERVED_ACCOUNT_NOT_DELETABLE(42216, "系统保留账号不允许删除"),
    /** 仓库暂无空闲库位（推荐引擎 B-B1）。 */
    NO_FREE_LOCATION(42217, "仓库暂无空闲库位"),
    /** 无满足硬约束的库位（重货层高规则过滤后候选为空，B-B1 / C-B7）。 */
    NO_ELIGIBLE_LOCATION(42218, "无满足约束的库位"),
    /** 推荐结果为空，无法采用（B-B1）。 */
    EMPTY_RECOMMENDATION(42219, "推荐结果为空"),
    /** 仿真过程中已无可用库位（C-B3）。 */
    SIM_NO_AVAILABLE_LOCATION(42220, "仿真过程中已无可用库位"),
    /** 出库仿真：订单货物当前不在任何库位（C-B4）。 */
    SKU_NOT_STORED(42221, "订单货物当前不在任何库位，无法出库"),
    /** 出库仿真：订单列表中无待出库订单（C-B4）。 */
    NO_PENDING_ORDER(42222, "没有可参与出库仿真的订单"),

    // ---------- 500 系统异常 ----------
    /** 数据库操作异常。 */
    DATABASE_ERROR(50001, "数据库操作异常"),
    /** 系统内部异常。 */
    SYSTEM_ERROR(50002, "系统异常，请稍后重试");
    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 获取业务错误码。
     *
     * @return 5 位业务错误码
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取默认错误提示。
     *
     * @return 中文提示信息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 由业务错误码反推 HTTP 状态码。
     *
     * <p>规则：取错误码前 3 位；非 100~599 范围时归为 500，避免非法状态码导致响应异常。
     *
     * @param code 业务错误码
     * @return 对应的 HTTP 状态码
     */
    public static HttpStatus toHttpStatus(int code) {
        int status = code / 100;
        HttpStatus resolved = HttpStatus.resolve(status);
        return resolved == null ? HttpStatus.INTERNAL_SERVER_ERROR : resolved;
    }
}
