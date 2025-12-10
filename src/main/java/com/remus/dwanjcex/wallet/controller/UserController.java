package com.remus.dwanjcex.wallet.controller;

import com.remus.dwanjcex.config.jwt.JwtUtils;
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
@RequestMapping("/auth") // 将路径改为/auth，更符合语义
@AllArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtUtils jwtUtils;

    @PostMapping("/register")
    public ResponseResult<Long> register(@RequestBody UserDto dto) {
        return ResponseResult.success(userService.genUser(dto.getUsername(), dto.getPassword()));
    }

    @PostMapping("/login")
    public ResponseResult<LoginResponseDto> login(@RequestBody UserDto dto) {
        // 1. 验证用户名和密码
        Long userId = userService.login(dto.getUsername(), dto.getPassword());

        // 2. 生成JWT
        String token = jwtUtils.generateToken(userId);

        // 3. 返回JWT
        return ResponseResult.success(new LoginResponseDto(token));
    }
}
