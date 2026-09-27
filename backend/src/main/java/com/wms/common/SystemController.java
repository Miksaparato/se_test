package com.wms.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统健康检查接口（脚手架自检用）。
 *
 * <p>路径不在《接口文档》62 个接口清单内，仅用于 COM-1 脚手架与联调阶段确认服务可用，
 * 验收前保留不影响契约。
 *
 * @author a
 */
@RestController
@RequestMapping("/api/v1")
public class SystemController {

    /**
     * 健康检查。
     *
     * @return 服务名、状态与服务器时间
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "wms-sim-backend");
        body.put("time", Instant.now().toString());
        return ApiResponse.ok(body);
    }
}
