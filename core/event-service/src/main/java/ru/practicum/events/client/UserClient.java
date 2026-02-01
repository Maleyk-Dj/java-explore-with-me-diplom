package ru.practicum.events.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.dto.users.UserShortDto;

import java.util.List;

@FeignClient(name = "USER-SERVICE", path = "/users")
public interface UserClient {
    @GetMapping("/{userId}/short")
    UserShortDto getUserShortById(@PathVariable Long userId);

    @GetMapping("/short")
    List<UserShortDto> getUsersShort(@RequestParam List<Long> ids);
}