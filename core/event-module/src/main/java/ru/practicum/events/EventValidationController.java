package ru.practicum.events;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.dto.events.EventValidationDto;
import ru.practicum.events.model.Event;
import ru.practicum.events.repository.EventRepository;
import ru.practicum.handler.exception.NotFoundException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/events")
public class EventValidationController {

    private final EventRepository eventRepository;

    @GetMapping("/{eventId}/validation")
    public EventValidationDto getEventForValidation(@PathVariable Long eventId) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() ->
                        new NotFoundException("Event with id=" + eventId + " not found"));

        return EventValidationDto.builder()
                .id(event.getId())
                .initiatorId(event.getInitiator())
                .participantLimit(event.getParticipantLimit())
                .requestModeration(event.getRequestModeration())
                .state(event.getState().name())
                .build();
    }
}
