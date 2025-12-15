package com.remus.dwanjcex.wallet.services;

import com.remus.dwanjcex.exception.BusinessException;
import com.remus.dwanjcex.wallet.entity.User;
import com.remus.dwanjcex.wallet.entity.result.ResultCode;
import com.remus.dwanjcex.wallet.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User register(String username, String password) {
        if (userMapper.findByUsername(username) != null) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS);
        }
        
        String passwordHash = passwordEncoder.encode(password);
        
        User user = User.builder()
                .username(username)
                .passwordHash(passwordHash)
                .status(1)
                .build();
        
        userMapper.insert(user);
        return user;
    }

    public User login(String username, String password) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS);
        }
        
        if (user.getStatus() != 1) {
            throw new BusinessException(ResultCode.USER_ACCOUNT_DISABLED);
        }

        return user;
    }
}
