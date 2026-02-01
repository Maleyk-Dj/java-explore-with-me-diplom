package ru.practicum.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.practicum.model.Stat;
import ru.practicum.model.ViewStats;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface StatRepository extends JpaRepository<Stat, Integer> {
    Collection<Stat> findByCreatedBetween(LocalDateTime start, LocalDateTime end);

    Collection<Stat> findByCreatedBetweenAndUriIn(LocalDateTime start, LocalDateTime end, Collection<String> uris);

    @Query("""
                SELECT new ru.practicum.model.ViewStats(
                    s.app,
                    s.uri,
                    COUNT(s.id)
                )
                FROM Stat s
                WHERE s.created BETWEEN :start AND :end
                GROUP BY s.app, s.uri
                ORDER BY COUNT(s.id) DESC
            """)
    List<ViewStats> getStatsAll(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT new ru.practicum.model.ViewStats(
                                                   s.app,
                    s.uri,
                    COUNT(s.id)
                )
                FROM Stat s
                WHERE s.created BETWEEN :start AND :end
                  AND s.uri IN :uris
                GROUP BY s.app, s.uri
                ORDER BY COUNT(s.id) DESC
            """)
    List<ViewStats> getStats(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("uris") Collection<String> uris
    );

    @Query("""
                SELECT new ru.practicum.model.ViewStats(
                    s.app,
                    s.uri,
                    COUNT(DISTINCT s.ip)
                )
                FROM Stat s
                WHERE s.created BETWEEN :start AND :end
                  AND s.uri IN :uris
                GROUP BY s.app, s.uri
                ORDER BY COUNT(DISTINCT s.ip) DESC
            """)
    List<ViewStats> getStatsUnique(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("uris") Collection<String> uris
    );

    @Query("""
                SELECT new ru.practicum.model.ViewStats(
                    s.app,
                    s.uri,
                    COUNT(DISTINCT s.ip)
                )
                FROM Stat s
                WHERE s.created BETWEEN :start AND :end
                GROUP BY s.app, s.uri
                ORDER BY COUNT(DISTINCT s.ip) DESC
            """)
    List<ViewStats> getStatsAllUnique(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

}

