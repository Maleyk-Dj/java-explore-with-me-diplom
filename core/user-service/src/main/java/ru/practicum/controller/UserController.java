package ru.practicum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.users.UserShortDto;
import ru.practicum.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/short")
    public List<UserShortDto> getUsersShort(
            @RequestParam List<Long> ids) {
        return userService.getUsersShort(ids);
    }

    @GetMapping("/{userId}/short")
    public UserShortDto getUserShort(@PathVariable Long userId) {
        return userService.getUserShort(userId);
    }
}
