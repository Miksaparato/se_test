package com.wms.domain.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JSON 列类型处理器基类：把 MySQL {@code JSON} 列与 Java 对象互转。
 *
 * <p>为什么不直接用 MyBatis-Plus 自带的 {@code JacksonTypeHandler}：它在 3.5.5 只接收
 * {@code Class<?>}，**泛型信息在运行时丢失**，{@code Map<String, LocationOccupancy>} 会被
 * 反序列化成 {@code Map<String, LinkedHashMap>}，取值时 ClassCastException。本基类要求子类
 * 显式提供 {@link TypeReference}，从而保留泛型（对应《代码规范》4.4「JSON 写入必须为合法 JSON」）。
 *
 * <p>所有处理器共用同一个 {@link ObjectMapper} 实例（线程安全），并忽略未知字段，
 * 以便后续给 JSON 结构**追加**键时不需要同步升级所有实例。
 *
 * @param <T> 目标 Java 类型
 * @author a
 */
public abstract class JsonTypeHandler<T> extends BaseTypeHandler<T> {

    /** 共享 ObjectMapper：忽略未知字段，容忍 JSON 结构后续扩展。 */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** 保留泛型的类型引用。 */
    private final TypeReference<T> typeReference;

    /**
     * 构造处理器。
     *
     * @param typeReference 目标类型的泛型引用，如 {@code new TypeReference<Map<String, Foo>>() {}}
     */
    protected JsonTypeHandler(TypeReference<T> typeReference) {
        this.typeReference = typeReference;
    }

    /**
     * 获取共享的 ObjectMapper（供子类做特殊处理）。
     *
     * @return ObjectMapper
     */
    protected static ObjectMapper mapper() {
        return MAPPER;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, toJson(parameter));
    }

    @Override
    public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    /**
     * 序列化为 JSON 字符串。
     *
     * @param value 非空对象
     * @return JSON 字符串
     * @throws SQLException 序列化失败时抛出，交由 MyBatis 包装上报
     */
    protected String toJson(T value) throws SQLException {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new SQLException("JSON 列序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 反序列化 JSON 字符串。
     *
     * @param json 数据库中的 JSON 文本，可为 null 或空串
     * @return 目标对象；入参为空时返回 null
     * @throws SQLException 反序列化失败时抛出，交由 MyBatis 包装上报
     */
    protected T parse(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeReference);
        } catch (Exception e) {
            throw new SQLException("JSON 列反序列化失败: " + e.getMessage(), e);
        }
    }
}
