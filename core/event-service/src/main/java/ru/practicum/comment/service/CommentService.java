package ru.practicum.comment.service;

import ru.practicum.dto.comment.CommentDto;
import ru.practicum.dto.comment.CommonCommentDto;
import ru.practicum.dto.comment.DeleteCommentDto;

import java.util.List;

public interface CommentService {
    CommentDto createComment(Long userId, Long eventId, CommonCommentDto newCommentDto);

    CommentDto updateComment(Long userId, Integer commentId, CommonCommentDto updateCommentDto);

    void deleteCommentByUser(Long userId, Integer commentId);

    List<CommentDto> getEventComments(Long eventId, Integer from, Integer size);

    List<CommentDto> getCommentsByUserId(Long userId);

    void deleteCommentByAdmin(DeleteCommentDto deleteCommentsDto);

    void deleteSingleCommentByAdmin(Long commentId);

    List<CommentDto> getUserEventComments(Long userId, Long eventId);

    List<CommentDto> getComments(String content, Long userId, Long eventId,
                                 String rangeStart, String rangeEnd, Integer from, Integer size);
}
