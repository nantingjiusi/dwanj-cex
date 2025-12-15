package com.remus.dwanjcex.wallet.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    private Long id;
    private String username;
    private String email;

    @JsonIgnore // 【关键修复】在序列化时忽略此字段，防止密码哈希泄露
    private String passwordHash;
    
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
