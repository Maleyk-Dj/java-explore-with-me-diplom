package ru.practicum.request.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.practicum.dto.events.EventValidationDto;

@FeignClient(name = "EVENT-SERVICE", path = "/events")
public interface EventClient {

    @GetMapping("/{eventId}/validation")
    EventValidationDto getEventForValidation(@PathVariable Long eventId);
}