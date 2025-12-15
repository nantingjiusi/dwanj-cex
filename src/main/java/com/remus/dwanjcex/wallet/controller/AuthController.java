package com.remus.dwanjcex.wallet.controller;

import com.remus.dwanjcex.config.jwt.JwtUtils;
import com.remus.dwanjcex.wallet.entity.User;
import com.remus.dwanjcex.wallet.entity.dto.LoginResponseDto;
import com.remus.dwanjcex.wallet.entity.dto.UserDto;
import com.remus.dwanjcex.wallet.entity.result.ResponseResult;
import com.remus.dwanjcex.wallet.services.UserService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@AllArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtils jwtUtils;

    @PostMapping("/register")
    public ResponseResult<?> register(@RequestBody UserDto dto) {
        userService.register(dto.getUsername(), dto.getPassword());
        return ResponseResult.success("Registration successful");
    }

    @PostMapping("/login")
    public ResponseResult<LoginResponseDto> login(@RequestBody UserDto dto) {
        User user = userService.login(dto.getUsername(), dto.getPassword());
        String token = jwtUtils.generateToken(user.getId());
        return ResponseResult.success(new LoginResponseDto(token, user));
    }
}
