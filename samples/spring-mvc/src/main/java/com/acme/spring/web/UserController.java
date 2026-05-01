package com.acme.spring.web;

import com.acme.spring.dto.UserDto;
import com.acme.spring.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public UserDto find(@PathVariable String id) {
        return userService.find(id);
    }

    @PostMapping("/{id}/rename")
    public void rename(@PathVariable String id, @RequestParam String name) {
        userService.rename(id, name);
    }
}

