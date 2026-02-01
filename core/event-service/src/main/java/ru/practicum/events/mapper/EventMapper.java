package ru.practicum.events.mapper;

import org.springframework.stereotype.Component;

import ru.practicum.category.model.Category;
import ru.practicum.dto.events.*;
import ru.practicum.dto.events.enums.EventState;
import ru.practicum.events.model.Event;
import ru.practicum.dto.users.UserShortDto;
import ru.practicum.dto.category.CategoryDto;

import java.time.format.DateTimeFormatter;


@Component
public class EventMapper {

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public EventFullDto toEventFullDto(Event event, UserShortDto initiator) {
        if (event == null) {
            return null;
        }

        EventFullDto dto = new EventFullDto();
        dto.setId(event.getId());
        dto.setAnnotation(event.getAnnotation());

        CategoryDto categoryDto = new CategoryDto(
                event.getCategory().getId(),
                event.getCategory().getName()
        );
        dto.setCategory(categoryDto);

        dto.setConfirmedRequests(
                event.getConfirmedRequests() != null ? event.getConfirmedRequests() : 0
        );

        dto.setCreatedOn(event.getCreatedOn() != null
                ? event.getCreatedOn().format(formatter)
                : null);

        dto.setDescription(event.getDescription());
        dto.setEventDate(event.getEventDate().format(formatter));

        // 🔥 Вот ключевой момент
        dto.setInitiator(initiator);

        Location location = new Location(
                event.getLocationLat(),
                event.getLocationLon()
        );
        dto.setLocation(location);

        dto.setPaid(event.getPaid());
        dto.setParticipantLimit(event.getParticipantLimit());
        dto.setPublishedOn(event.getPublishedOn() != null
                ? event.getPublishedOn().format(formatter)
                : null);
        dto.setRequestModeration(event.getRequestModeration());
        dto.setState(event.getState().name());
        dto.setTitle(event.getTitle());
        dto.setViews(event.getViews() != null ? event.getViews() : 0);

        return dto;
    }

    public EventShortDto toEventShortDto(Event event) {
        return toEventShortDto(event, null);
    }


    public EventShortDto toEventShortDto(Event event,
                                         UserShortDto initiator) {

        if (event == null) {
            return null;
        }

        EventShortDto dto = new EventShortDto();

        dto.setId(event.getId());
        dto.setAnnotation(event.getAnnotation());

        CategoryDto categoryDto = new CategoryDto(
                event.getCategory().getId(),
                event.getCategory().getName()
        );
        dto.setCategory(categoryDto);

        dto.setConfirmedRequests(
                event.getConfirmedRequests() != null
                        ? event.getConfirmedRequests()
                        : 0
        );

        dto.setEventDate(event.getEventDate().format(formatter));

        // 🔥 просто устанавливаем уже полученного пользователя
        dto.setInitiator(initiator);

        dto.setPaid(event.getPaid());
        dto.setTitle(event.getTitle());
        dto.setViews(event.getViews() != null ? event.getViews() : 0);

        return dto;
    }


    public Event toEventForUpdate(Long userId, Long eventId, UpdateEventUserRequest request) {
        if (request == null) return null;

        Event event = new Event();
        event.setId(eventId);
        event.setInitiator(userId);

        if (request.getTitle() != null) {
            event.setTitle(request.getTitle());
        }
        if (request.getAnnotation() != null) {
            event.setAnnotation(request.getAnnotation());
        }
        if (request.getDescription() != null) {
            event.setDescription(request.getDescription());
        }
        if (request.getEventDate() != null) {
            event.setEventDate(request.getEventDate());
        }
        if (request.getPaid() != null) {
            event.setPaid(request.getPaid());
        }
        if (request.getParticipantLimit() != null) {
            event.setParticipantLimit(request.getParticipantLimit());
        }
        if (request.getRequestModeration() != null) {
            event.setRequestModeration(request.getRequestModeration());
        }
        if (request.getLocation() != null) {
            event.setLocationLat(request.getLocation().getLat());
            event.setLocationLon(request.getLocation().getLon());
        }

        return event;
    }

    public Event toEvent(Long userId,
                         NewEventDto dto,
                         Category category) {

        return Event.builder()
                .annotation(dto.getAnnotation())
                .category(category)
                .description(dto.getDescription())
                .eventDate(dto.getEventDate())
                .initiator(userId)   // 🔥 ключевой момент
                .locationLat(dto.getLocation().getLat())
                .locationLon(dto.getLocation().getLon())
                .paid(dto.getPaid())
                .participantLimit(dto.getParticipantLimit())
                .requestModeration(dto.getRequestModeration())
                .title(dto.getTitle())
                .state(EventState.PENDING)
                .build();
    }

}