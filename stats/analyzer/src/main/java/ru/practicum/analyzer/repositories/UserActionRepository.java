package ru.practicum.analyzer.repositories;

import jakarta.persistence.Tuple;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.practicum.analyzer.model.UserAction;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserActionRepository extends JpaRepository<UserAction, Long> {
    Optional<UserAction> findByUserIdAndEventId(long userId, long eventId);

    @Query(value = """
            SELECT ua.* 
            FROM user_actions ua
            WHERE ua.user_id = :userId 
            ORDER BY ua.timestamp_at DESC 
            LIMIT :max
            """, nativeQuery = true)
    List<UserAction> findAllByUserIdWithLimit(@Param("userId") long userId, @Param("max") long max);

    @Query("""
            SELECT ua.eventId, SUM(ua.weight)
            FROM UserAction ua
            WHERE ua.eventId IN (:eventIds)
            GROUP BY ua.eventId
            """)
    List<Tuple> findSumMaxWeight(@Param("eventIds") List<Long> eventIds);

    List<UserAction> findByUserIdAndEventIdIn(@Param("userId") long userId, @Param("eventIds") Collection<Long> eventIds);
}