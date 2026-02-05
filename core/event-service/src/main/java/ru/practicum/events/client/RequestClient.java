package ru.practicum.events.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ru.practicum.dto.requests.EventRequestStatusUpdateResult;
import ru.practicum.dto.requests.ParticipationRequestDto;
import ru.practicum.dto.requests.RequestStatusUpdateRequest;

import java.util.List;
import java.util.Map;

@FeignClient(name = "REQUEST-SERVICE", path = "/requests")
public interface RequestClient {

    @PostMapping("/confirmed-counts")
    Map<Long, Integer> getConfirmedCounts(@RequestBody List<Long> eventIds);

    @GetMapping("/events/{eventId}/confirmed-count")
    Integer getConfirmedCount(@PathVariable Long eventId);

    @GetMapping("/events/{eventId}")
    List<ParticipationRequestDto> getRequestsByEvent(@PathVariable Long eventId);

    @PostMapping("/{eventId}/status")
    EventRequestStatusUpdateResult changeRequestsStatus(
            @PathVariable Long eventId,
            @RequestBody RequestStatusUpdateRequest request);

    @GetMapping("/events/{eventId}/users/{userId}/visited")
    boolean hasUserVisitedEvent(@PathVariable Long eventId,
                                @PathVariable Long userId);

}
