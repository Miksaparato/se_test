package com.wms.data.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 更新用户请求体（API-007）。
 *
 * <p>仅允许更新姓名、邮箱、手机号、备注与关联角色；**账号与密码不在此接口修改**
 * （账号是登录凭据，改密走 API-004 或后续单独的改密接口）。
 *
 * @param name   姓名/昵称
 * @param email  邮箱
 * @param phone  手机号
 * @param remark 备注
 * @param roles  角色标识列表；为 null 表示不改动角色，为空数组表示清空角色
 * @author a
 */
public record UpdateUserRequest(
        @Size(max = 64, message = "姓名长度不能超过 64 个字符")
        String name,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
        String email,

        @Size(max = 32, message = "手机号长度不能超过 32 个字符")
        String phone,

        @Size(max = 255, message = "备注长度不能超过 255 个字符")
        String remark,

        List<@Pattern(regexp = "^[a-z][a-z0-9_-]{1,31}$", message = "角色标识格式不正确") String> roles) {
}
