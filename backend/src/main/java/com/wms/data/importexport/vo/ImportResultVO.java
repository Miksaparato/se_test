package com.wms.data.importexport.vo;

import java.util.List;

/**
 * 导入结果（API-037 / API-038）。
 *
 * <p>设计要点：**永不因个别行非法而整体失败**——合法行照常入库，非法行把
 * 「物理行号 + 原因」逐条返回，用户可直接定位修改（FR-1.2 验收 2）。
 *
 * @param total    解析到的数据行总数（不含表头与空行）
 * @param inserted 新增成功行数
 * @param updated  更新成功行数（仅当 strategy = update 时可能出现）
 * @param skipped  跳过行数（重复编码且 strategy = skip）
 * @param failed   失败行数
 * @param errors   失败明细（最多返回 {@code MAX_ERROR_ITEMS} 条，避免响应过大）
 * @param truncated 错误明细是否被截断
 * @author a
 */
public record ImportResultVO(
        int total,
        int inserted,
        int updated,
        int skipped,
        int failed,
        List<ImportError> errors,
        boolean truncated) {

    /** 错误明细返回上限。 */
    public static final int MAX_ERROR_ITEMS = 100;

    /**
     * 单行错误。
     *
     * @param line   文件中的物理行号（含表头，与 Excel/编辑器显示一致）
     * @param column 出错的列名，行级错误时为 null
     * @param message 错误原因
     */
    public record ImportError(int line, String column, String message) {
    }

    /**
     * 构造导入结果，并裁剪超量错误明细。
     *
     * @param total     总行数
     * @param inserted  新增行数
     * @param updated   更新行数
     * @param skipped   跳过行数
     * @param errors    全部错误明细
     * @return 导入结果
     */
    public static ImportResultVO of(int total, int inserted, int updated, int skipped,
                                    List<ImportError> errors) {
        boolean truncated = errors.size() > MAX_ERROR_ITEMS;
        List<ImportError> limited = truncated ? errors.subList(0, MAX_ERROR_ITEMS) : errors;
        return new ImportResultVO(total, inserted, updated, skipped, errors.size(),
                List.copyOf(limited), truncated);
    }
}
