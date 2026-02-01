package ru.practicum.request.service;

import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.requests.EventRequestStatusUpdateResult;
import ru.practicum.dto.requests.ParticipationRequestDto;
import ru.practicum.dto.requests.RequestStatusUpdateRequest;

import java.util.List;
import java.util.Map;

public interface RequestService {
    ParticipationRequestDto createRequest(Long userId, Long eventId);

    // Метод для получения всех запросов пользователя
    List<ParticipationRequestDto> getUserRequests(Long userId);

    // Метод для отмены запроса
    ParticipationRequestDto cancelRequest(Long userId, Long requestId);

    @Transactional(readOnly = true)
    Map<Long, Integer> getConfirmedCountsForEvents(List<Long> eventIds);

    Integer getConfirmedCountForEvent(Long eventId);

    List<ParticipationRequestDto> getRequestsByEvent(Long eventId);

    @Transactional
    EventRequestStatusUpdateResult changeRequestsStatus(
            Long eventId,
            RequestStatusUpdateRequest updateRequest);
}
