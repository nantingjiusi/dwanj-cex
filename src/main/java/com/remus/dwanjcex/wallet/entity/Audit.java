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
public class Audit {
    private Long id;
    private Long userId;
    private String action;
    private String target;
    private String beforeText;
    private String afterText;
    private LocalDateTime createdAt;
}
