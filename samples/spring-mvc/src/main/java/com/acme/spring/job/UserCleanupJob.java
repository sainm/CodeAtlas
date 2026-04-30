package com.acme.spring.job;

import com.acme.spring.service.UserService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UserCleanupJob {
    private final UserService userService;

    public UserCleanupJob(UserService userService) {
        this.userService = userService;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void refreshDailyUser() {
        userService.find("system");
    }

    @Async
    public void renameInBackground(String id, String name) {
        userService.rename(id, name);
    }
}

