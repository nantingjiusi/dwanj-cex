package com.remus.dwanjcex.wallet.controller;

import com.remus.dwanjcex.wallet.entity.dto.UserDto;
import com.remus.dwanjcex.wallet.entity.result.ResponseResult;
import com.remus.dwanjcex.wallet.services.UserService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@AllArgsConstructor
public class UserController {

    private final UserService userService;



    @PostMapping("/register")
    public ResponseResult<Long> register(@RequestBody UserDto dto) {
        return ResponseResult.success(userService.genUser(dto.getUsername(), dto.getPassword()));
    }
}
