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
public class SystemConfig {
    private Integer id;
    private String configKey;
    private String configValue;
    private String description;
    private LocalDateTime updatedAt;
}
