package com.remus.dwanjcex.wallet.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.math.BigDecimal;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class UserBalance {
    @Id
    private Long id;

    private Long userId;


    private String asset; // e.g. BTC/USDT


    private BigDecimal available = BigDecimal.ZERO;


    private BigDecimal frozen = BigDecimal.ZERO;

}