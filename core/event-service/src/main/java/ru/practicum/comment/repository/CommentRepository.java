package ru.practicum.comment.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.practicum.comment.model.Comment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    Optional<Comment> findByUserIdAndId(Long userId, Integer commentId);

    Page<Comment> findByEventId(Long eventId, Pageable pageable);

    List<Comment> findByUserId(Long userId);

    List<Comment> findByIdIn(List<Long> commentIds);

    void deleteByIdIn(List<Long> commentIds);

    List<Comment> findByUserIdAndEventIdOrderByCreatedDesc(Long userId, Long eventId);

    @Query("SELECT c FROM Comment c " +
            "WHERE (:text IS NULL OR LOWER(c.text) LIKE LOWER(CONCAT('%', :text, '%'))) " +
            "AND (:userId IS NULL OR c.userId = :userId) " +
            "AND (:eventId IS NULL OR c.eventId = :eventId) " +
            "AND c.created >= :rangeStart " +
            "AND c.created <= :rangeEnd")
    Page<Comment> getComments(@Param("text") String text,
                              @Param("userId") Long userId,
                              @Param("eventId") Long eventId,
                              @Param("rangeStart") LocalDateTime rangeStart,
                              @Param("rangeEnd") LocalDateTime rangeEnd,
                              Pageable pageable);

}
