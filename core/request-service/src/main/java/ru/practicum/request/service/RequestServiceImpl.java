package ru.practicum.request.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.events.EventValidationDto;
import ru.practicum.dto.events.enums.EventState;
import ru.practicum.dto.requests.EventRequestStatusUpdateResult;
import ru.practicum.dto.requests.RequestStatusUpdateRequest;
import ru.practicum.dto.requests.ParticipationRequestDto;
import ru.practicum.dto.requests.RequestStatus;
import ru.practicum.request.client.EventClient;
import ru.practicum.request.client.UserClient;
import ru.practicum.request.count.ConfirmedCount;
import ru.practicum.request.handler.exception.ConflictException;
import ru.practicum.request.handler.exception.NotFoundException;
import ru.practicum.request.handler.exception.ValidationException;
import ru.practicum.request.mapper.RequestMapper;
import ru.practicum.request.model.Request;
import ru.practicum.request.storage.RequestRepository;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final RequestMapper requestMapper;
    private final UserClient userClient;
    private final EventClient eventClient;

    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {

        // 1️⃣ Проверяем пользователя
        userClient.getUserShortById(userId);

        // 2️⃣ Получаем событие
        EventValidationDto event =
                eventClient.getEventForValidation(eventId);

        // 3️⃣ Дубликат
        if (requestRepository.existsByRequesterIdAndEventId(userId, eventId)) {
            throw new ConflictException("Request already exists");
        }

        // 4️⃣ Инициатор не может участвовать
        if (event.getInitiatorId().equals(userId)) {
            throw new ConflictException("Event initiator cannot request participation");
        }

        // 5️⃣ Проверка публикации
        if (!EventState.PUBLISHED.name().equals(event.getState())) {
            throw new ConflictException("Cannot participate in unpublished event");
        }

        Integer confirmedCount = getConfirmedCountForEvent(eventId);

        // 6️⃣ Проверка лимита
        if (event.getParticipantLimit() != null
                && event.getParticipantLimit() != 0
                && confirmedCount >= event.getParticipantLimit()) {
            throw new ConflictException("Participant limit reached");
        }

        // 7️⃣ Авто-подтверждение
        boolean autoConfirm =
                Boolean.FALSE.equals(event.getRequestModeration())
                        || event.getParticipantLimit() == 0;

        Request request = Request.builder()
                .created(LocalDateTime.now())
                .eventId(eventId)
                .requesterId(userId)
                .status(autoConfirm
                        ? RequestStatus.CONFIRMED
                        : RequestStatus.PENDING)
                .build();

        return requestMapper.toParticipationRequestDto(
                requestRepository.save(request)
        );
    }


    @Override
    @Transactional(readOnly = true)
    public List<ParticipationRequestDto> getUserRequests(Long userId) {

        // 1️⃣ Проверяем пользователя через user-service
        userClient.getUserShortById(userId);

        // 2️⃣ Получаем заявки
        List<Request> requests =
                requestRepository.findByRequesterId(userId);

        return requests.stream()
                .map(requestMapper::toParticipationRequestDto)
                .toList();
    }


    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        Request request = requestRepository.findByRequesterIdAndId(userId, requestId)
                .orElseThrow(() -> new NotFoundException("Request with id=" + requestId + " for user id=" + userId + " was not found"));

        request.setStatus(RequestStatus.CANCELED);

        return requestMapper.toParticipationRequestDto(requestRepository.save(request));
    }

    @Override
    public Map<Long, Integer> getConfirmedCountsForEvents(List<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<ConfirmedCount> counts = requestRepository.countConfirmedForEventIds(eventIds);

        Map<Long, Integer> result = new HashMap<>();
        for (ConfirmedCount count : counts) {
            result.put(count.getEventId(), count.getCnt());
        }

        // Заполняем 0 для событий без заявок
        for (Long eventId : eventIds) {
            result.putIfAbsent(eventId, 0);
        }

        return result;
    }


    @Override
    public Integer getConfirmedCountForEvent(Long eventId) {
        Integer count = requestRepository.countConfirmedByEventId(eventId);
        return count != null ? count : 0;
    }

    @Override
    public List<ParticipationRequestDto> getRequestsByEvent(Long eventId) {

        List<Request> requests =
                requestRepository.findByEventId(eventId);

        return requests.stream()
                .map(requestMapper::toParticipationRequestDto)
                .toList();
    }

    @Transactional
    @Override
    public EventRequestStatusUpdateResult changeRequestsStatus(
            Long eventId,
            RequestStatusUpdateRequest updateRequest) {

        if (updateRequest.getRequestIds() == null
                || updateRequest.getRequestIds().isEmpty()) {
            throw new ValidationException("requestIds must be not empty");
        }

        RequestStatus targetStatus = updateRequest.getStatus();
        if (targetStatus == null || targetStatus == RequestStatus.PENDING) {
            throw new ValidationException("Invalid target status");
        }

        List<Request> requests =
                requestRepository.findAllById(updateRequest.getRequestIds());

        if (requests.size() != updateRequest.getRequestIds().size()) {
            throw new NotFoundException("One or more requests not found");
        }

        // Проверяем принадлежность событию
        for (Request r : requests) {
            if (!r.getEventId().equals(eventId)) {
                throw new ConflictException(
                        "Request id=" + r.getId() +
                                " does not belong to event id=" + eventId);
            }
        }

        // Только PENDING можно менять
        if (requests.stream()
                .anyMatch(r -> r.getStatus() != RequestStatus.PENDING)) {
            throw new ConflictException(
                    "Only requests with status PENDING can be changed");
        }

        List<ParticipationRequestDto> confirmed = new ArrayList<>();
        List<ParticipationRequestDto> rejected = new ArrayList<>();

        if (targetStatus == RequestStatus.CONFIRMED) {

            Integer confirmedCount =
                    requestRepository.countByEventIdAndStatus(
                            eventId, RequestStatus.CONFIRMED);

            confirmedCount = confirmedCount == null ? 0 : confirmedCount;

            for (Request req : requests) {
                req.setStatus(RequestStatus.CONFIRMED);
                confirmed.add(
                        requestMapper.toParticipationRequestDto(
                                requestRepository.save(req)
                        )
                );
            }

        } else if (targetStatus == RequestStatus.REJECTED) {

            for (Request req : requests) {
                req.setStatus(RequestStatus.REJECTED);
                rejected.add(
                        requestMapper.toParticipationRequestDto(
                                requestRepository.save(req)
                        )
                );
            }
        }

        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(confirmed)
                .rejectedRequests(rejected)
                .build();
    }


}
