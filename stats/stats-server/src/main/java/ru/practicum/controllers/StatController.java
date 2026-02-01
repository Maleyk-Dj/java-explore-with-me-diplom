package ru.practicum.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.service.StatService;
import ru.practicum.statistics.dto.EndpointHitDto;
import ru.practicum.statistics.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;

@Validated
@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor
public class StatController {
    private final StatService statService;
    private static final String DATE_TIME_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";


    @PostMapping("/hit")
    @ResponseStatus(HttpStatus.CREATED)
    public EndpointHitDto hit(@RequestBody EndpointHitDto request) {
        return statService.hit(request);
    }


    @GetMapping("/stats")
    public Collection<ViewStatsDto> getStats(
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(required = false) List<String> uris,
            @RequestParam(defaultValue = "false") boolean unique
    ) {
        LocalDateTime startTime;
        LocalDateTime endTime;

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            startTime = LocalDateTime.parse(start, formatter);
            endTime = LocalDateTime.parse(end, formatter);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Неверный формат даты");
        }

        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("Дата начала позже даты окончания");
        }

        return statService.getStats(startTime, endTime, uris, unique);
    }

}


