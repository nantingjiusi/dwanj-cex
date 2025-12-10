package com.remus.dwanjcex.wallet.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功后返回给前端的数据。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/18 18:45
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDto {

    /**
     * JWT (JSON Web Token)
     */
    private String token;
}
