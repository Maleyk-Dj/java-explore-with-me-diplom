package ru.practicum.comment.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.dto.comment.DeleteCommentDto;
import ru.practicum.comment.service.CommentService;


@RestController
@RequiredArgsConstructor
public class CommentAdminController {
    private final CommentService commentService;

    @DeleteMapping("/admin/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCommentByAdmin(@PathVariable @Positive Long commentId) {
        commentService.deleteSingleCommentByAdmin(commentId);
    }

    @DeleteMapping("/admin/comments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCommentsByAdmin(@Valid @RequestBody DeleteCommentDto deleteCommentDto) {
        commentService.deleteCommentByAdmin(deleteCommentDto);
    }
}
