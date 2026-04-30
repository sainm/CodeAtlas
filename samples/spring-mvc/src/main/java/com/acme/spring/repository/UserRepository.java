package com.acme.spring.repository;

import com.acme.spring.dto.UserDto;
import com.acme.spring.mapper.UserMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final UserMapper userMapper;

    public UserRepository(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public UserDto findById(String id) {
        return userMapper.findById(id);
    }

    public void rename(String id, String name) {
        userMapper.rename(id, name);
    }
}

