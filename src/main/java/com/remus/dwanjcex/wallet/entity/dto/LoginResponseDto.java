package com.remus.dwanjcex.wallet.entity.dto;

import com.remus.dwanjcex.wallet.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDto {
    private String token;
    private User user;
}
