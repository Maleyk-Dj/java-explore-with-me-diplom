package ru.practicum.request.mapper;

import org.springframework.stereotype.Component;
import ru.practicum.dto.requests.ParticipationRequestDto;
import ru.practicum.request.model.Request;

import java.time.format.DateTimeFormatter;

@Component
public class RequestMapper {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ParticipationRequestDto toParticipationRequestDto(Request request) {
        if (request == null) {
            return null;
        }

        ParticipationRequestDto dto = new ParticipationRequestDto();
        dto.setId(request.getId());
        dto.setCreated(request.getCreated().format(FORMATTER)); // Форматируем дату в строку
        dto.setEvent(request.getEventId());
        dto.setRequester(request.getRequesterId());
        dto.setStatus(request.getStatus().name()); // Получаем имя статуса из enum

        return dto;
    }
}