package com.acme.legacy.service;

import com.acme.legacy.mapper.UserMapper;
import com.acme.legacy.dao.UserJdbcDao;
import java.sql.Connection;

public class UserService {
    private final UserMapper userMapper = new UserMapper();
    private final UserJdbcDao userJdbcDao = new UserJdbcDao();

    public void save(String userId, String name, String description) {
        userMapper.updateUser(userId, name, description);
    }

    public void loadForLegacyJdbc(Connection connection, String userId) throws Exception {
        userJdbcDao.touchUser(connection, userId);
    }
}
