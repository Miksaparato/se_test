package com.wms.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 统一分页结果，对应《接口文档》1.2 分页列表结构。
 *
 * <pre>
 * {
 *   "code": 0,
 *   "message": "success",
 *   "data": { "list": [], "total": 0, "page": 1, "page_size": 20 }
 * }
 * </pre>
 *
 * <p>约定（《代码规范》6）：{@code page} 从 1 开始；{@code page_size} 默认 20、上限 100。
 * 注意对外 JSON 字段为下划线形式 {@code page_size}，故组件名直接采用该写法并由
 * {@link JsonProperty} 固定，避免不同 Jackson 版本的命名策略差异。
 *
 * @param list      当前页数据
 * @param total     总记录数
 * @param page      当前页码，从 1 开始
 * @param page_size 每页条数
 * @param <T>       列表元素类型
 * @author a
 */
public record PageResult<T>(
        List<T> list,
        long total,
        int page,
        @JsonProperty("page_size") int page_size) {

    /** 默认每页条数。 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 每页条数上限。 */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * 由 MyBatis-Plus 分页对象构造，元素类型不变。
     *
     * @param page MyBatis-Plus 分页结果
     * @param <T>  元素类型
     * @return 统一分页结果
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize());
    }

    /**
     * 由 MyBatis-Plus 分页对象构造，并将实体逐条转换为 VO。
     *
     * @param page       MyBatis-Plus 分页结果（实体）
     * @param converter  实体 → VO 的转换函数
     * @param <E>        实体类型
     * @param <T>        VO 类型
     * @return 统一分页结果（VO）
     */
    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> converter) {
        List<T> converted = page.getRecords().stream().map(converter).toList();
        return new PageResult<>(converted, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    /**
     * 构造空分页结果（用于无数据场景，保持字段结构一致）。
     *
     * @param page     当前页码
     * @param pageSize 每页条数
     * @param <T>      元素类型
     * @return 空分页结果
     */
    public static <T> PageResult<T> empty(int page, int pageSize) {
        return new PageResult<>(Collections.emptyList(), 0L, page, pageSize);
    }

    /**
     * 规整页码：小于 1 时归为 1。
     *
     * @param page 原始页码
     * @return 合法页码
     */
    public static int normalizePage(Integer page) {
        return (page == null || page < 1) ? 1 : page;
    }

    /**
     * 规整每页条数：为空归为默认值，超过上限时截断（《代码规范》6）。
     *
     * @param pageSize 原始每页条数
     * @return 合法每页条数
     */
    public static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
