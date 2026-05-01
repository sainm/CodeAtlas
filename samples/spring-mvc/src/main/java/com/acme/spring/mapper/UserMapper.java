package com.acme.spring.mapper;

import com.acme.spring.dto.UserDto;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {
    UserDto findById(String id);

    void rename(String id, String name);
}

