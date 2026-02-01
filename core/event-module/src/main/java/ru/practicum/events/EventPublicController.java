package ru.practicum.events;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.events.EventFullDto;
import ru.practicum.dto.events.EventShortDto;
import ru.practicum.events.params.PublicEventParams;
import ru.practicum.events.repository.EventRepository;
import ru.practicum.events.service.EventService;

import javax.naming.ServiceUnavailableException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Validated
public class EventPublicController {
    private final EventService eventService;
    private final EventRepository eventRepository;

    @GetMapping("/{eventId}")
    public EventFullDto getEvent(@PathVariable Long eventId, HttpServletRequest request) throws ServiceUnavailableException {
        return eventService.getPublicEventById(eventId, request);
    }

    @GetMapping
    public ResponseEntity<List<EventShortDto>> searchEvents(
            @ModelAttribute PublicEventParams params,
            HttpServletRequest request) {
        List<EventShortDto> events = eventService.searchPublicEvents(params, request);
        return ResponseEntity.ok(events);
    }

}

