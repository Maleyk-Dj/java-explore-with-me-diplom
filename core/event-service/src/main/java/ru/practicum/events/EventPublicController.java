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
    public EventFullDto getEvent(
            @PathVariable Long eventId,
            @RequestHeader("X-EWM-USER-ID") long userId
    ) throws ServiceUnavailableException {
        return eventService.getPublicEventById(eventId, userId);
    }


    @GetMapping
    public ResponseEntity<List<EventShortDto>> searchEvents(
            @ModelAttribute PublicEventParams params) {

        return ResponseEntity.ok(
                eventService.searchPublicEvents(params)
        );
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<EventShortDto>> getRecommendations(
            @RequestHeader("X-EWM-USER-ID") long userId,
            @RequestParam(defaultValue = "10") int size) {

        return ResponseEntity.ok(
                eventService.getRecommendations(userId, size)
        );
    }

    @PutMapping("/{eventId}/like")
    public ResponseEntity<Void> likeEvent(
            @PathVariable Long eventId,
            @RequestHeader("X-EWM-USER-ID") long userId) {

        eventService.likeEvent(userId, eventId);
        return ResponseEntity.ok().build();
    }



}

