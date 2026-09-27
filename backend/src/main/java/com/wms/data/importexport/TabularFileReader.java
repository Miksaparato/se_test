package com.wms.data.importexport;

import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 导入文件解析调度器（A-B4）：按文件名后缀选择 CSV 或 Excel 解析器。
 *
 * @author a
 */
@Component
public class TabularFileReader {

    private final List<TabularParser> parsers;

    /**
     * 构造调度器。
     *
     * @param parsers Spring 注入的全部解析器实现（插件式，新增格式只需新增实现）
     */
    public TabularFileReader(List<TabularParser> parsers) {
        this.parsers = parsers;
    }

    /**
     * 解析上传文件。
     *
     * @param file 上传文件
     * @return 数据行列表
     */
    public List<TabularParser.Row> read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "上传文件不能为空");
        }
        String filename = file.getOriginalFilename();
        TabularParser parser = parsers.stream()
                .filter(candidate -> candidate.supports(filename))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID,
                        "不支持的文件格式，仅支持 .csv / .xlsx / .xls，实际文件名：" + filename));
        try (InputStream inputStream = file.getInputStream()) {
            return parser.parse(inputStream);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件解析失败：" + ex.getMessage());
        }
    }
}
