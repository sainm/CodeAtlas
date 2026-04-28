package com.acme.legacy.service;

import com.acme.legacy.mapper.UserMapper;

public class UserService {
    private final UserMapper userMapper = new UserMapper();

    public void save(String userId, String name, String description) {
        userMapper.updateUser(userId, name, description);
    }
}
