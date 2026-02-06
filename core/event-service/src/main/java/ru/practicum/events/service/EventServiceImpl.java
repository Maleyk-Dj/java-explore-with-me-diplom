package ru.practicum.events.service;

import feign.FeignException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.practicum.client.CollectorGrpcClient;
import ru.practicum.client.RecommendationsGrpcClient;
import ru.practicum.dto.events.*;
import ru.practicum.dto.requests.RequestStatus;
import ru.practicum.category.model.Category;
import ru.practicum.category.storage.CategoryRepository;
import ru.practicum.dto.events.enums.EventState;
import ru.practicum.dto.events.enums.EventStateAction;
import ru.practicum.dto.users.UserShortDto;
import ru.practicum.events.client.RequestClient;
import ru.practicum.events.client.UserClient;
import ru.practicum.events.mapper.EventMapper;
import ru.practicum.events.model.Event;
import ru.practicum.events.params.AdminEventParams;
import ru.practicum.events.params.PublicEventParams;
import ru.practicum.events.repository.EventRepository;
import ru.practicum.dto.requests.EventRequestStatusUpdateResult;
import ru.practicum.dto.requests.ParticipationRequestDto;
import ru.practicum.dto.requests.RequestStatusUpdateRequest;
import ru.practicum.grpc.stats.action.ActionTypeProto;
import ru.practicum.grpc.stats.analyzer.RecommendedEventProto;
import ru.practicum.handler.exception.ConflictException;
import ru.practicum.handler.exception.NotFoundException;
import ru.practicum.handler.exception.ServiceUnavailableException;
import ru.practicum.handler.exception.ValidationException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class EventServiceImpl implements EventService {
    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final EventMapper eventMapper;
    private final UserClient userClient;
    private final RecommendationsGrpcClient recommendationsGrpcClient;
    private final CollectorGrpcClient collectorGrpcClient;
    private final RequestClient requestClient;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    @Override
    public List<EventFullDto> search(AdminEventParams params) {

        validatePaginationParams(params);

        Pageable pageable = PageRequest.of(
                params.getFrom() / params.getSize(),
                params.getSize()
        );

        Specification<Event> spec = buildAdminSpecification(params);
        Page<Event> events = eventRepository.findAll(spec, pageable);

        List<Event> content = events.getContent();

        if (content.isEmpty()) {
            return Collections.emptyList();
        }

        // --- 1. ID событий ---
        List<Long> eventIds = content.stream()
                .map(Event::getId)
                .toList();

        // Подтверждённые заявки
        Map<Long, Integer> confirmedRequestsMap =
                getConfirmedRequestsForEvents(eventIds);

        // Рейтинг через Analyzer (gRPC)
        Map<Long, Double> ratingMap =
                getRatingsForEvents(eventIds);

        // --- 2. Собираем initiatorId ---
        List<Long> userIds = content.stream()
                .map(Event::getInitiator)
                .distinct()
                .toList();

        // --- 3. Получаем пользователей одним вызовом ---
        List<UserShortDto> users = userClient.getUsersShort(userIds);

        Map<Long, UserShortDto> usersMap = users.stream()
                .collect(Collectors.toMap(
                        UserShortDto::getId,
                        u -> u
                ));

        // --- 4. Маппинг ---
        return content.stream()
                .map(event -> {
                    UserShortDto initiator =
                            usersMap.get(event.getInitiator());

                    EventFullDto dto =
                            eventMapper.toEventFullDto(event, initiator);

                    dto.setConfirmedRequests(
                            confirmedRequestsMap.getOrDefault(event.getId(), 0)
                    );

                    dto.setRating(
                            ratingMap.getOrDefault(event.getId(), 0.0)
                    );

                    return dto;
                })
                .toList();
    }


    private Map<Long, Double> getRatingsForEvents(List<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            return recommendationsGrpcClient
                    .getInteractionsCount(eventIds)
                    .collect(Collectors.toMap(
                            RecommendedEventProto::getEventId,
                            RecommendedEventProto::getScore
                    ));

        } catch (Exception e) {
            log.warn("Analyzer unavailable, default rating = 0.0");
            return eventIds.stream()
                    .collect(Collectors.toMap(id -> id, id -> 0.0));
        }
    }

    private double getEventRating(Long eventId) {
        return getRatingsForEvents(List.of(eventId))
                .getOrDefault(eventId, 0.0);
    }


    private Map<Long, Integer> getConfirmedRequestsForEvents(List<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Integer> confirmedMap = new HashMap<>();

        for (Long eventId : eventIds) {
            try {
                return requestClient.getConfirmedCounts(eventIds);
            } catch (Exception e) {
                log.warn("Failed to get confirmed requests for event {}: {}", eventId, e.getMessage());
                confirmedMap.put(eventId, 0);
            }
        }

        return eventIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> 0
                ));
    }


    private Long extractEventIdFromUri(String uri) {
        if (uri == null || !uri.startsWith("/events/")) {
            return null;
        }
        try {
            return Long.parseLong(uri.substring("/events/".length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }


    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId,
                                           UpdateEventAdminRequest dto) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId + " not found"
                ));

        if (dto.getCategory() != null) {
            Category category = categoryRepository.findById(dto.getCategory())
                    .orElseThrow(() -> new NotFoundException("Category not found"));
            event.setCategory(category);
        }

        updateEventFields(event, dto);
        handleStateAction(event, dto.getStateAction());

        Event updatedEvent = eventRepository.save(event);

        UserShortDto initiator =
                userClient.getUserShortById(updatedEvent.getInitiator());

        return eventMapper.toEventFullDto(updatedEvent, initiator);
    }

    @Transactional
    @Override
    public EventFullDto getPublicEventById(Long eventId, long userId)
            throws ServiceUnavailableException {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() ->
                        new NotFoundException("Event with id=" + eventId + " not found"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event with id=" + eventId + " is not published");
        }

        // 1️⃣ Отправляем VIEW в Collector (gRPC)
        try {
            collectorGrpcClient.collectUserAction(
                    userId,
                    eventId,
                    ActionTypeProto.ACTION_VIEW,
                    Instant.now()
            );
        } catch (Exception e) {
            log.warn("Collector unavailable, VIEW not recorded");
        }

        // 2️⃣ Получаем рейтинг через Analyzer
        double rating;
        try {
            rating = recommendationsGrpcClient
                    .getInteractionsCount(List.of(eventId))
                    .map(RecommendedEventProto::getScore)
                    .findFirst()
                    .orElse(0.0);
        } catch (Exception e) {
            log.warn("Analyzer unavailable, default rating = 0.0");
            rating = 0.0;
        }

        // 3️⃣ confirmed requests
        Integer confirmed;
        try {
            confirmed = requestClient.getConfirmedCount(eventId);
        } catch (Exception e) {
            confirmed = 0;
        }

        // 4️⃣ инициатор
        UserShortDto initiator =
                userClient.getUserShortById(event.getInitiator());

        // 5️⃣ маппинг
        EventFullDto dto =
                eventMapper.toEventFullDto(event, initiator);

        dto.setConfirmedRequests(
                confirmed != null ? confirmed : 0
        );

        dto.setRating(rating);

        return dto;
    }

    @Override
    public List<EventShortDto> searchPublicEvents(PublicEventParams params) {

        validatePublicParams(params);

        Pageable pageable = buildPageable(params);

        List<Event> events =
                eventRepository.findAll(buildPublicSpec(params), pageable)
                        .getContent();

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        // 1️⃣ confirmed requests
        Map<Long, Integer> confirmedMap =
                fetchConfirmedCounts(events);

        // 2️⃣ фильтр onlyAvailable
        events = applyOnlyAvailableFilter(events, confirmedMap, params);

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        // 3️⃣ получаем рейтинги через Analyzer
        List<Long> eventIds = events.stream()
                .map(Event::getId)
                .toList();

        Map<Long, Double> ratingMap =
                getRatingsForEvents(eventIds);

        // 4️⃣ маппинг
        List<EventShortDto> dtos = events.stream()
                .map(event -> {
                    EventShortDto dto =
                            eventMapper.toEventShortDto(event);

                    dto.setConfirmedRequests(
                            confirmedMap.getOrDefault(event.getId(), 0)
                    );

                    dto.setRating(
                            ratingMap.getOrDefault(event.getId(), 0.0)
                    );

                    return dto;
                })
                .toList();

        // 5️⃣ сортировка (если RATING)
        return applySortIfNeeded(dtos, params);
    }

    @Override
    public List<EventShortDto> getRecommendations(long userId, int size) {

        // 1️⃣ Получаем рекомендации через Analyzer
        List<Long> recommendedEventIds;

        try {
            recommendedEventIds = recommendationsGrpcClient
                    .getRecommendationsForUser(userId, size)
                    .map(RecommendedEventProto::getEventId)
                    .toList();
        } catch (Exception e) {
            log.warn("Analyzer unavailable, returning empty recommendations");
            return Collections.emptyList();
        }

        if (recommendedEventIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 2️⃣ Загружаем события из БД
        List<Event> events =
                eventRepository.findAllById(recommendedEventIds);

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        // 3️⃣ confirmed requests
        Map<Long, Integer> confirmedMap =
                fetchConfirmedCounts(events);

        // 4️⃣ Получаем rating (чтобы DTO были консистентны)
        Map<Long, Double> ratingMap =
                getRatingsForEvents(recommendedEventIds);

        // 5️⃣ Батч-загрузка инициаторов
        List<Long> userIds = events.stream()
                .map(Event::getInitiator)
                .distinct()
                .toList();

        List<UserShortDto> users =
                userClient.getUsersShort(userIds);

        Map<Long, UserShortDto> usersMap =
                users.stream()
                        .collect(Collectors.toMap(
                                UserShortDto::getId,
                                u -> u
                        ));

        // 6️⃣ Маппинг
        return events.stream()
                .map(event -> {
                    UserShortDto initiator =
                            usersMap.get(event.getInitiator());

                    EventShortDto dto =
                            eventMapper.toEventShortDto(event, initiator);

                    dto.setConfirmedRequests(
                            confirmedMap.getOrDefault(event.getId(), 0)
                    );

                    dto.setRating(
                            ratingMap.getOrDefault(event.getId(), 0.0)
                    );

                    return dto;
                })
                .toList();
    }

    @Override
    @Transactional
    public void likeEvent(long userId, long eventId) {

        // 1️⃣ Проверяем, что событие существует
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() ->
                        new NotFoundException("Event with id=" + eventId + " not found"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event with id=" + eventId + " is not published");
        }

        // 2️⃣ Проверяем, что пользователь посещал мероприятие
        try {
            boolean hasVisited =
                    requestClient.hasUserVisitedEvent(userId, eventId);

            if (!hasVisited) {
                throw new ValidationException(
                        "User can like only events they have visited"
                );
            }

        } catch (FeignException.NotFound e) {
            throw new ValidationException(
                    "User can like only events they have visited"
            );
        } catch (Exception e) {
            log.warn("Failed to verify visit status: {}", e.getMessage());
            throw new ru.practicum.handler.exception.ServiceUnavailableException("Request service unavailable");
        }

        // 3️⃣ Отправляем LIKE в Collector
        try {
            collectorGrpcClient.collectUserAction(
                    userId,
                    eventId,
                    ActionTypeProto.ACTION_LIKE,
                    Instant.now()
            );
        } catch (Exception e) {
            log.warn("Collector unavailable, LIKE not recorded");
            throw new ServiceUnavailableException("Collector unavailable");
        }

        log.info("User {} liked event {}", userId, eventId);
    }


    @Override
    @Transactional(readOnly = true)
    public List<ParticipationRequestDto> getRequestsByEvent(Long userId, Long eventId) {

        // 1️⃣ Проверяем событие
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId + " was not found."));

        // 2️⃣ Проверяем, что пользователь инициатор
        if (!event.getInitiator().equals(userId)) {
            throw new ConflictException(
                    "User with id=" + userId +
                            " is not the initiator of event with id=" + eventId);
        }

        // 3️⃣ Получаем заявки через request-service
        return requestClient.getRequestsByEvent(eventId);
    }


    private Specification<Event> buildAdminSpecification(AdminEventParams params) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Фильтр по пользователям
            if (params.getUsers() != null && !params.getUsers().isEmpty()) {
                predicates.add(root.get("initiator").in(params.getUsers()));
            }

            // Фильтр по состояниям
            if (params.getStates() != null && !params.getStates().isEmpty()) {
                List<EventState> eventStates = params.getStates().stream()
                        .map(state -> {
                            try {
                                return EventState.valueOf(state.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                throw new ValidationException("Invalid state: " + state);
                            }
                        })
                        .collect(Collectors.toList());
                predicates.add(root.get("state").in(eventStates));
            }

            // Фильтр по категориям
            if (params.getCategories() != null && !params.getCategories().isEmpty()) {
                predicates.add(root.get("category").get("id").in(params.getCategories()));
            }

            // Фильтр по дате начала
            if (params.getRangeStart() != null) {
                LocalDateTime start = parseDateTime(params.getRangeStart());
                predicates.add(cb.greaterThanOrEqualTo(root.get("eventDate"), start));
            }

            // Фильтр по дате окончания
            if (params.getRangeEnd() != null) {
                LocalDateTime end = parseDateTime(params.getRangeEnd());
                predicates.add(cb.lessThanOrEqualTo(root.get("eventDate"), end));
            }

            // Валидация диапазона дат
            if (params.getRangeStart() != null && params.getRangeEnd() != null) {
                LocalDateTime start = parseDateTime(params.getRangeStart());
                LocalDateTime end = parseDateTime(params.getRangeEnd());
                if (end.isBefore(start)) {
                    throw new ValidationException("RangeEnd cannot be before rangeStart");
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult changeRequestsStatus(
            Long userId,
            Long eventId,
            RequestStatusUpdateRequest updateRequest) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() ->
                        new NotFoundException("Event with id=" + eventId + " not found"));

        if (!event.getInitiator().equals(userId)) {
            throw new ConflictException(
                    "User with id=" + userId +
                            " is not initiator of event with id=" + eventId);
        }

        // 🔥 Проверка лимита ДО делегирования
        if (updateRequest.getStatus() == RequestStatus.CONFIRMED
                && event.getParticipantLimit() != null
                && event.getParticipantLimit() > 0) {

            Integer confirmed =
                    requestClient.getConfirmedCount(eventId);

            confirmed = confirmed == null ? 0 : confirmed;

            if (confirmed >= event.getParticipantLimit()) {
                throw new ConflictException("Participant limit reached");
            }
        }

        try {
            return requestClient.changeRequestsStatus(eventId, updateRequest);
        } catch (FeignException.Conflict ex) {
            throw new ConflictException(
                    "Only requests with status PENDING can be changed");
        }
    }


    private void updateEventFields(Event event, UpdateEventAdminRequest dto) {
        if (dto.getAnnotation() != null) {
            validateAnnotation(dto.getAnnotation());
            event.setAnnotation(dto.getAnnotation());
        }

        if (dto.getDescription() != null) {
            validateDescription(dto.getDescription());
            event.setDescription(dto.getDescription());
        }

        if (dto.getTitle() != null) {
            validateTitle(dto.getTitle());
            event.setTitle(dto.getTitle());
        }

        if (dto.getEventDate() != null) {
            validateEventDate(dto.getEventDate());
            event.setEventDate(dto.getEventDate());
        }

        if (dto.getLocation() != null) {
            event.setLocationLat(dto.getLocation().getLat());
            event.setLocationLon(dto.getLocation().getLon());
        }

        if (dto.getPaid() != null) {
            event.setPaid(dto.getPaid());
        }

        if (dto.getParticipantLimit() != null) {
            validateParticipantLimit(dto.getParticipantLimit());
            event.setParticipantLimit(dto.getParticipantLimit());
        }

        if (dto.getRequestModeration() != null) {
            event.setRequestModeration(dto.getRequestModeration());
        }
    }

    private void handleStateAction(Event event, EventStateAction stateAction) {
        if (stateAction == null) return;

        switch (stateAction) {
            case PUBLISH_EVENT:
                validatePublishEvent(event);
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
                break;

            case REJECT_EVENT:
                validateRejectEvent(event);
                event.setState(EventState.CANCELED);
                break;
        }
    }

    private void validatePublishEvent(Event event) {
        if (event.getState() != EventState.PENDING) {
            throw new ConflictException("Cannot publish event that is not in PENDING state");
        }

        if (event.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
            throw new ConflictException("Cannot publish event less than 1 hour before event date");
        }

    }

    private void validateRejectEvent(Event event) {
        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Cannot reject already published event");
        }
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        try {
            return LocalDateTime.parse(dateTimeStr, formatter);
        } catch (DateTimeParseException e) {
            throw new ValidationException("Invalid date format. Expected: yyyy-MM-dd HH:mm:ss");
        }
    }

    private void validateEventDate(LocalDateTime eventDate) {
        if (eventDate.isBefore(LocalDateTime.now())) {
            throw new ValidationException("Event date cannot be in the past");
        }
    }

    private void validatePaginationParams(AdminEventParams params) {
        if (params.getFrom() < 0) {
            throw new ValidationException("From must be >= 0");
        }
        if (params.getSize() <= 0) {
            throw new ValidationException("Size must be > 0");
        }
        if (params.getSize() > 1000) {
            throw new ValidationException("Size cannot exceed 1000");
        }
    }

    private void validateAnnotation(String annotation) {
        if (annotation.length() < 20 || annotation.length() > 2000) {
            throw new ValidationException("Annotation must be between 20 and 2000 characters");
        }
        if (annotation.trim().isEmpty()) {
            throw new ValidationException("Annotation cannot be empty or contain only spaces");
        }
    }

    private void validateDescription(String description) {
        if (description.length() < 20 || description.length() > 7000) {
            throw new ValidationException("Description must be between 20 and 7000 characters");
        }
        if (description.trim().isEmpty()) {
            throw new ValidationException("Description cannot be empty or contain only spaces");
        }
    }

    private void validateTitle(String title) {
        if (title.length() < 3 || title.length() > 120) {
            throw new ValidationException("Title must be between 3 and 120 characters");
        }
        if (title.trim().isEmpty()) {
            throw new ValidationException("Title cannot be empty or contain only spaces");
        }
    }

    private void validateParticipantLimit(Integer participantLimit) {
        if (participantLimit < 0) {
            throw new ValidationException("Participant limit cannot be negative");
        }
    }

    @Override
    public EventFullDto add(Long userId, NewEventDto newEventDto) {

        // 1️⃣ Проверяем категорию (как было)
        Category category = categoryRepository.findById(newEventDto.getCategory())
                .orElseThrow(() -> new NotFoundException(
                        "Категория с id = " + newEventDto.getCategory() + " не найдена."
                ));

        // 2️⃣ Проверяем существование пользователя через user-service
        UserShortDto initiator = userClient.getUserShortById(userId);
        // Если пользователя нет — Feign выбросит исключение

        // 3️⃣ Создаём событие (теперь передаём только userId)
        Event event = eventMapper.toEvent(userId, newEventDto, category);

        event = eventRepository.save(event);

        log.info("Добавлено новое событие {}.", event);

        // 4️⃣ Возвращаем DTO с инициатором
        return eventMapper.toEventFullDto(event, initiator);
    }

    @Override
    @Transactional
    public EventFullDto update(Long userId,
                               Long eventId,
                               UpdateEventUserRequest updateEventUserRequest) {

        // 1️⃣ Проверяем существование пользователя
        UserShortDto initiator = userClient.getUserShortById(userId);

        // 2️⃣ Получаем событие
        Event oldEvent = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(
                        "Событие с id = " + eventId + " не найдено."
                ));

        // 3️⃣ Проверяем, что пользователь — инициатор
        if (!oldEvent.getInitiator().equals(userId)) {
            throw new ConflictException("User is not initiator of this event");
        }

        // 4️⃣ Бизнес-логика без изменений
        if (oldEvent.getState() == EventState.PUBLISHED)
            throw new ConflictException(
                    "Only pending or canceled events can be changed."
            );

        if (updateEventUserRequest.getCategoryId() != null) {
            Category category = categoryRepository
                    .findById(updateEventUserRequest.getCategoryId())
                    .orElseThrow(() -> new NotFoundException(
                            "Категория с id = "
                                    + updateEventUserRequest.getCategoryId()
                                    + " не найдена."
                    ));
            oldEvent.setCategory(category);
        }

        if (updateEventUserRequest.getTitle() != null) {
            oldEvent.setTitle(updateEventUserRequest.getTitle());
        }
        if (updateEventUserRequest.getAnnotation() != null) {
            oldEvent.setAnnotation(updateEventUserRequest.getAnnotation());
        }
        if (updateEventUserRequest.getDescription() != null) {
            oldEvent.setDescription(updateEventUserRequest.getDescription());
        }
        if (updateEventUserRequest.getEventDate() != null) {
            oldEvent.setEventDate(updateEventUserRequest.getEventDate());
        }
        if (updateEventUserRequest.getPaid() != null) {
            oldEvent.setPaid(updateEventUserRequest.getPaid());
        }
        if (updateEventUserRequest.getParticipantLimit() != null) {
            oldEvent.setParticipantLimit(updateEventUserRequest.getParticipantLimit());
        }
        if (updateEventUserRequest.getRequestModeration() != null) {
            oldEvent.setRequestModeration(updateEventUserRequest.getRequestModeration());
        }
        if (updateEventUserRequest.getLocation() != null) {
            oldEvent.setLocationLat(updateEventUserRequest.getLocation().getLat());
            oldEvent.setLocationLon(updateEventUserRequest.getLocation().getLon());
        }
        if (updateEventUserRequest.getStateAction() != null) {
            oldEvent.setState(
                    updateEventUserRequest.getStateAction() == EventState.CANCELED
                            ? EventState.CANCELED
                            : EventState.PENDING
            );
        }

        eventRepository.save(oldEvent);

        log.info("Пользователем обновлены данные события {}.", oldEvent);

        // 5️⃣ Передаём инициатора в маппер
        return eventMapper.toEventFullDto(oldEvent, initiator);
    }


    @Override
    @Transactional(readOnly = true)
    public List<EventShortDto> findAllByUser(Long userId, int from, int size) {

        // 1️⃣ Проверяем существование пользователя через user-service
        UserShortDto initiator = userClient.getUserShortById(userId);

        // 2️⃣ Используем нормальную пагинацию через PageRequest
        int page = from / size;

        Page<Event> eventsPage =
                eventRepository.findByInitiatorOrderByIdAsc(
                        userId,
                        PageRequest.of(page, size)
                );

        List<Event> events = eventsPage.getContent();

        log.info("Получен список событий пользователя с id {} и параметрами: from = {}, size = {}.",
                userId, from, size);

        // 3️⃣ Маппинг
        return events.stream()
                .map(event -> eventMapper.toEventShortDto(event, initiator))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EventFullDto findByUserAndEvent(Long userId, Long eventId) {

        // 1️⃣ Проверяем пользователя
        UserShortDto initiator =
                userClient.getUserShortById(userId);

        // 2️⃣ Получаем событие
        Event event = eventRepository
                .findByInitiatorAndId(userId, eventId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId + " was not found"
                ));

        log.info("Получены данные по событию c id = {} у пользователя с id = {}.",
                eventId, userId);

        // 3️⃣ Получаем рейтинг через Analyzer
        double rating;
        try {
            rating = recommendationsGrpcClient
                    .getInteractionsCount(List.of(eventId))
                    .map(RecommendedEventProto::getScore)
                    .findFirst()
                    .orElse(0.0);
        } catch (Exception e) {
            log.warn("Analyzer unavailable, default rating = 0.0");
            rating = 0.0;
        }

        // 4️⃣ Маппинг
        EventFullDto dto =
                eventMapper.toEventFullDto(event, initiator);

        dto.setRating(rating);

        return dto;
    }

    private List<EventShortDto> applySortIfNeeded(
            List<EventShortDto> dtos,
            PublicEventParams params) {

        if (!"RATING".equalsIgnoreCase(params.getSort()))
            return dtos;

        return dtos.stream()
                .sorted(Comparator.comparing(
                        EventShortDto::getRating,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }


    private List<Event> applyOnlyAvailableFilter(
            List<Event> events,
            Map<Long, Integer> confirmedMap,
            PublicEventParams params) {

        if (!Boolean.TRUE.equals(params.getOnlyAvailable()))
            return events;

        return events.stream()
                .filter(e -> {
                    Integer confirmed =
                            confirmedMap.getOrDefault(e.getId(), 0);
                    return e.getParticipantLimit() == 0
                            || confirmed < e.getParticipantLimit();
                })
                .toList();
    }

    private Map<Long, Integer> fetchConfirmedCounts(List<Event> events) {

        if (events.isEmpty()) return Collections.emptyMap();

        try {
            List<Long> ids = events.stream()
                    .map(Event::getId)
                    .toList();

            return requestClient.getConfirmedCounts(ids);

        } catch (Exception ex) {
            log.warn("Failed to fetch confirmed counts: {}", ex.getMessage());
            return Collections.emptyMap();
        }
    }


    private Pageable buildPageable(PublicEventParams params) {

        int from = params.getFrom() == null ? 0 : params.getFrom();
        int size = params.getSize() == null ? 10 : params.getSize();

        if ("RATING".equalsIgnoreCase(params.getSort())) {
            return PageRequest.of(from / size, size);
        }


        return PageRequest.of(from / size, size,
                Sort.by("eventDate").ascending());
    }

    private void validatePublicParams(PublicEventParams params) {

        if (params.getFrom() != null && params.getFrom() < 0)
            throw new ValidationException("from must be >= 0");

        if (params.getSize() != null &&
                (params.getSize() <= 0 || params.getSize() > 1000))
            throw new ValidationException("size must be between 1 and 1000");

        if (params.getRangeStart() != null && params.getRangeEnd() != null) {

            LocalDateTime start = parseDateTime(params.getRangeStart());
            LocalDateTime end = parseDateTime(params.getRangeEnd());

            if (end.isBefore(start)) {
                throw new ValidationException("rangeEnd cannot be before rangeStart");
            }
        }
    }


    private Specification<Event> buildPublicSpec(PublicEventParams params) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("state"), EventState.PUBLISHED));

            if (params.getText() != null && !params.getText().isBlank()) {
                String pat = "%" + params.getText().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("annotation")), pat),
                        cb.like(cb.lower(root.get("description")), pat)
                ));
            }

            if (params.getCategories() != null && !params.getCategories().isEmpty()) {
                predicates.add(root.get("category").get("id").in(params.getCategories()));
            }

            if (params.getPaid() != null) {
                predicates.add(cb.equal(root.get("paid"), params.getPaid()));
            }

            LocalDateTime now = LocalDateTime.now();

            boolean noRange =
                    (params.getRangeStart() == null || params.getRangeStart().isBlank())
                            && (params.getRangeEnd() == null || params.getRangeEnd().isBlank());

            if (noRange) {
                predicates.add(cb.greaterThan(root.get("eventDate"), now));
            } else {
                if (params.getRangeStart() != null && !params.getRangeStart().isBlank()) {
                    predicates.add(cb.greaterThanOrEqualTo(
                            root.get("eventDate"),
                            LocalDateTime.parse(params.getRangeStart(), formatter)
                    ));
                }
                if (params.getRangeEnd() != null && !params.getRangeEnd().isBlank()) {
                    predicates.add(cb.lessThanOrEqualTo(
                            root.get("eventDate"),
                            LocalDateTime.parse(params.getRangeEnd(), formatter)
                    ));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<EventShortDto> mapToShortDtos(
            List<Event> events,
            Map<Long, Double> ratingMap,
            Map<Long, Integer> confirmedMap) {

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        // 1️⃣ собираем уникальных инициаторов
        List<Long> userIds = events.stream()
                .map(Event::getInitiator)
                .distinct()
                .toList();

        // 2️⃣ получаем пользователей одним вызовом
        List<UserShortDto> users = userClient.getUsersShort(userIds);

        Map<Long, UserShortDto> usersMap = users.stream()
                .collect(Collectors.toMap(
                        UserShortDto::getId,
                        u -> u
                ));

        // 3️⃣ маппинг
        return events.stream()
                .map(e -> {
                    UserShortDto initiator =
                            usersMap.get(e.getInitiator());

                    EventShortDto dto =
                            eventMapper.toEventShortDto(e, initiator);

                    dto.setConfirmedRequests(
                            confirmedMap.getOrDefault(e.getId(), 0)
                    );

                    dto.setRating(
                            ratingMap.getOrDefault(e.getId(), 0.0)
                    );

                    return dto;
                })
                .toList();
    }
}
