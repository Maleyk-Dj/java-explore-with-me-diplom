package ru.practicum.events.service;

import jakarta.servlet.http.HttpServletRequest;
import ru.practicum.dto.events.*;
import ru.practicum.events.params.AdminEventParams;
import ru.practicum.events.params.PublicEventParams;
import ru.practicum.dto.requests.EventRequestStatusUpdateResult;
import ru.practicum.dto.requests.ParticipationRequestDto;
import ru.practicum.dto.requests.RequestStatusUpdateRequest;


import javax.naming.ServiceUnavailableException;
import java.util.List;

public interface EventService {
    List<EventFullDto> search(AdminEventParams params);

    EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest dto);

    public EventFullDto add(Long userId, NewEventDto newEventDto);

    public EventFullDto update(Long userId, Long eventId, UpdateEventUserRequest updateEventUserRequest);

    EventFullDto getPublicEventById(Long eventId, HttpServletRequest request) throws ServiceUnavailableException;

    List<EventShortDto> searchPublicEvents(PublicEventParams params, HttpServletRequest request);

    EventRequestStatusUpdateResult changeRequestsStatus(Long userId, Long eventId, RequestStatusUpdateRequest updateRequest);

    List<EventShortDto> findAllByUser(Long userId, int from, int size);

    EventFullDto findByUserAndEvent(Long userId, Long eventId);

    List<ParticipationRequestDto> getRequestsByEvent(Long userId, Long eventId);
}
