package com.wms.data.permission;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wms.data.permission.vo.PermissionVO;
import com.wms.domain.entity.Permission;
import com.wms.domain.mapper.PermissionMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限服务：权限清单查询与权限码合法性校验（API-015）。
 *
 * <p>权限码清单是**冻结契约**（《接口文档》1.4），禁止自行发明；新增权限须由 a 更新
 * 数据库 {@code permissions} 表与《接口文档》后再使用（《代码规范》4.3）。
 *
 * @author a
 */
@Service
public class PermissionService {

    private final PermissionMapper permissionMapper;

    /**
     * 构造权限服务。
     *
     * @param permissionMapper 权限 Mapper
     */
    public PermissionService(PermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    /**
     * 权限清单（API-015），按 sort_no 升序，便于前端渲染权限树。
     *
     * @return 权限列表
     */
    public List<PermissionVO> listPermissions() {
        return permissionMapper.selectList(
                        Wrappers.<Permission>lambdaQuery()
                                .orderByAsc(Permission::getSortNo)
                                .orderByAsc(Permission::getId))
                .stream()
                .map(PermissionVO::from)
                .toList();
    }

    /**
     * 权限码 → 权限 id 映射。
     *
     * @return 映射（按 id 升序）
     */
    public Map<String, Long> loadPermissionIdByCode() {
        Map<String, Long> mapping = new LinkedHashMap<>();
        for (Permission permission : permissionMapper.selectList(
                Wrappers.<Permission>lambdaQuery().orderByAsc(Permission::getId))) {
            mapping.put(permission.getCode(), permission.getId());
        }
        return mapping;
    }

    /**
     * 校验权限码是否全部合法（存在于清单中）。
     *
     * @param codes 权限码列表
     * @throws com.wms.common.BizException 存在非法权限码时抛 42212
     */
    public void validateCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return;
        }
        Map<String, Long> known = loadPermissionIdByCode();
        List<String> invalid = codes.stream()
                .filter(code -> code == null || !known.containsKey(code))
                .map(code -> code == null ? "null" : code)
                .distinct()
                .toList();
        if (!invalid.isEmpty()) {
            throw new com.wms.common.BizException(com.wms.common.ErrorCode.PERMISSION_CODE_INVALID,
                    "存在非法的权限码：" + String.join(", ", invalid));
        }
    }
}
