package ru.practicum.analyzer.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.practicum.analyzer.model.EventSimilarity;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventSimilarityRepository extends JpaRepository<EventSimilarity, Long> {
    Optional<EventSimilarity> findByEventAAndEventB(long eventA, long eventB);

    @Query(value = """
            SELECT es.*
            FROM events_similarity es
            WHERE (es.eventA_id = :eventId OR es.eventB_id = :eventId)
            AND NOT EXISTS ( SELECT 1
                               FROM user_actions ua1
                               JOIN user_actions ua2 ON ua1.user_id = ua2.user_id
                               WHERE ua1.user_id = :userId
                               AND ((ua1.event_id = es.eventA_id AND ua2.event_id = es.eventB_id)
                               OR (ua1.event_id = es.eventB_id AND ua2.event_id = es.eventA_id))
            )
            ORDER BY es.score DESC
            LIMIT :max
            """, nativeQuery = true)
    List<EventSimilarity> GetSimilarEvents(@Param("eventId") long eventId, @Param("userId") long userId,
                                           @Param("max") long max);

    @Query(value = """
            SELECT DISTINCT es.*
            FROM events_similarity es
            WHERE (es.eventA_id = ANY(:eventIds) OR es.eventB_id = ANY(:eventIds))
            AND NOT EXISTS ( SELECT 1
                                FROM user_actions ua
                                WHERE ua.user_id = :userId
                                AND ((es.eventA_id = ANY(:eventIds) AND ua.event_id = es.eventB_id)
                                OR(es.eventB_id = ANY(:eventIds) AND ua.event_id = es.eventA_id))
            )
            ORDER BY es.score DESC
            LIMIT :max
            """, nativeQuery = true)
    List<EventSimilarity> findSimilarNotViewedByUser(@Param("eventIds") List<Long> eventIds, @Param("userId") long userId,
                                                     @Param("max") long max);

    @Query(value = """
            SELECT es.*
            FROM events_similarity es
            WHERE (
                (es.eventA_id = ANY(:eventIds) AND EXISTS (
                    SELECT 1 FROM user_actions ua
                    WHERE ua.user_id = :userId AND ua.event_id = es.eventB_id
                ))
                OR
                (es.eventB_id = ANY(:eventIds) AND EXISTS (
                    SELECT 1 FROM user_actions ua
                    WHERE ua.user_id = :userId AND ua.event_id = es.eventA_id
                ))
            )
            ORDER BY es.score DESC
            """, nativeQuery = true)
    List<EventSimilarity> findViewedNeighbors(
            @Param("eventIds") List<Long> eventIds, @Param("userId") long userId
    );
}
