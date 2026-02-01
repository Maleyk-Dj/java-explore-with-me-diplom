package ru.practicum.service;


import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.users.NewUserRequest;
import ru.practicum.dto.users.UserDto;
import ru.practicum.dto.users.UserShortDto;
import ru.practicum.param.AdminUserParam;

import java.util.List;

public interface UserService {
    UserDto create(NewUserRequest request);

    List<UserDto> getUsers(AdminUserParam param);

    void delete(Long userId);

    List<UserShortDto> getUsersShort(List<Long> ids);

    @Transactional(readOnly = true)
    UserShortDto getUserShort(Long userId);
}
