package com.acme.legacy.dao;

import java.sql.Connection;

public class UserJdbcDao {
    public void touchUser(Connection connection, String userId) throws Exception {
        String sql = "select user_id, name from users where user_id = ?";
        connection.prepareStatement(sql);
    }
}

