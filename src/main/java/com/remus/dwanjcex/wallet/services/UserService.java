package com.remus.dwanjcex.wallet.services;

import com.remus.dwanjcex.wallet.entity.User;
import com.remus.dwanjcex.wallet.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserService {


    private UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }


    public Long genUser(String username,String pwd){
        User u = User.builder().username(username).password(pwd).status(1).createdAt(LocalDateTime.now()).build();
        userMapper.insert(u);
        return u.getId();
    }
}
