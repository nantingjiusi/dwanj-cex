package com.remus.dwanjcex.wallet.services;

import com.remus.dwanjcex.exception.BusinessException;
import com.remus.dwanjcex.wallet.entity.User;
import com.remus.dwanjcex.wallet.entity.result.ResultCode;
import com.remus.dwanjcex.wallet.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public Long genUser(String username, String pwd) {
        // TODO: 在生产环境中，密码必须使用BCrypt等强哈希算法进行加密存储。
        User u = User.builder().username(username).password(pwd).status(1).createdAt(LocalDateTime.now()).build();
        userMapper.insert(u);
        return u.getId();
    }

    /**
     * 用户登录验证。
     *
     * @param username 用户名
     * @param password 密码
     * @return 如果验证成功，返回用户ID；否则抛出业务异常。
     */
    public Long login(String username, String password) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // TODO: 在生产环境中，这里应该是BCrypt的matches方法，而不是简单的字符串比较。
        if (!Objects.equals(user.getPassword(), password)) {
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS);
        }

        return user.getId();
    }
}
