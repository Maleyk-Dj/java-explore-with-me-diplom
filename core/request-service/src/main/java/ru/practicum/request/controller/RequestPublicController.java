package ru.practicum.request.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.requests.EventRequestStatusUpdateResult;
import ru.practicum.dto.requests.ParticipationRequestDto;
import ru.practicum.dto.requests.RequestStatusUpdateRequest;
import ru.practicum.request.service.RequestService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/requests") // Убрали /internal
@RequiredArgsConstructor
@Slf4j
public class RequestPublicController {

    private final RequestService requestService;

    @GetMapping("/events/{eventId}/confirmed-count")
    public Integer getConfirmedCount(@PathVariable Long eventId) {
        log.info("Public endpoint: get confirmed count for event {}", eventId);
        return requestService.getConfirmedCountForEvent(eventId);
    }

    @PostMapping("/confirmed-counts")
    public Map<Long, Integer> getConfirmedCounts(
            @RequestBody List<Long> eventIds) {
        log.info("Public endpoint: get confirmed counts for events {}", eventIds);
        return requestService.getConfirmedCountsForEvents(eventIds);
    }

    @GetMapping("/events/{eventId}")
    public List<ParticipationRequestDto> getRequestsByEvent(
            @PathVariable Long eventId) {
        log.info("Public endpoint: get requests for event {}", eventId);
        return requestService.getRequestsByEvent(eventId);
    }

    @PostMapping("/{eventId}/status")
    public EventRequestStatusUpdateResult changeStatus(
            @PathVariable Long eventId,
            @RequestBody RequestStatusUpdateRequest request) {
        log.info("Public endpoint: change status for event {}", eventId);
        return requestService.changeRequestsStatus(eventId, request);
    }
}