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
public class UserProfile {
    private Long userId;
    private String fullName;
    private String country;
    private String phone;
    private Integer kycLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
