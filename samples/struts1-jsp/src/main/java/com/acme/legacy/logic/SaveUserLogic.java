package com.acme.legacy.logic;

import com.acme.legacy.service.UserService;

public class SaveUserLogic {
    private final UserService userService = new UserService();

    public void execute(String userId, String name, String description) {
        userService.save(userId, name, description);
    }
}

