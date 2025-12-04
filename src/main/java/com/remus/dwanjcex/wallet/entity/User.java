package com.remus.dwanjcex.wallet.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    private Long id;                // 用户ID，主键
    private String username;        // 登录用户名
    private String password;        // 哈希密码
    private String email;           // 邮箱
    private String phone;           // 手机号
    private Integer status;         // 用户状态 0=禁用,1=启用
    private LocalDateTime createdAt; // 创建时间
    private LocalDateTime updatedAt; // 更新时间




}