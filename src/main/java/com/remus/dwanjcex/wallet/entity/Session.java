package com.remus.dwanjcex.wallet.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {
    private Long id;
    private Long userId;
    private String sessionToken;
    private String ip;
    private String userAgent;
    private LocalDateTime createdAt;
    private LocalDateTime expiredAt;
}
