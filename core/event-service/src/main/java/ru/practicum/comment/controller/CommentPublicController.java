package ru.practicum.comment.controller;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.comment.CommentDto;
import ru.practicum.comment.service.CommentService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/comments")

public class CommentPublicController {
    private final CommentService commentService;

    @GetMapping("/events/{eventId}/comment")
    @ResponseStatus(HttpStatus.OK)
    public List<CommentDto> getEventComments(
            @PathVariable @Positive Long eventId,
            @RequestParam(defaultValue = "0") Integer from,
            @RequestParam(defaultValue = "10") Integer size) {
        return commentService.getEventComments(eventId, from, size);
    }

    @GetMapping("/comment/search")
    @ResponseStatus(HttpStatus.OK)
    public List<CommentDto> searchComments(
            @RequestParam(required = false) String content,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) String rangeStart,
            @RequestParam(required = false) String rangeEnd,
            @RequestParam(defaultValue = "0") Integer from,
            @RequestParam(defaultValue = "10") Integer size) {
        return commentService.getComments(content, userId, eventId, rangeStart, rangeEnd, from, size);
    }
}
