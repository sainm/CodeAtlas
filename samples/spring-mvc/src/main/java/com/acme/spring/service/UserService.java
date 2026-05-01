package com.acme.spring.service;

import com.acme.spring.dto.UserDto;
import com.acme.spring.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserDto find(String id) {
        return userRepository.findById(id);
    }

    public void rename(String id, String name) {
        userRepository.rename(id, name);
    }
}

