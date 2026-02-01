package ru.practicum.dto.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventValidationDto {

    private Long id;
    private Long initiatorId;
    private Boolean requestModeration;
    private Integer participantLimit;
    private String state; // PUBLISHED / etc
}
