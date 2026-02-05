package ru.practicum.request.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.practicum.request.count.ConfirmedCount;
import ru.practicum.dto.requests.RequestStatus;
import ru.practicum.request.model.Request;

import java.util.List;
import java.util.Optional;

@Repository
public interface RequestRepository extends JpaRepository<Request, Long> {

    @Query("""
            select r.eventId as eventId,
                   count(r) as cnt
            from Request r
            where r.eventId in :eventIds
              and r.status = 'CONFIRMED'
            group by r.eventId
            """)
    List<ConfirmedCount> countConfirmedForEventIds(@Param("eventIds") List<Long> eventIds);

    @Query("""
            select count(r)
            from Request r
            where r.eventId = :eventId
              and r.status = 'CONFIRMED'
            """)
    Integer countConfirmedByEventId(@Param("eventId") Long eventId);


    List<Request> findByRequesterId(Long requesterId);

    Optional<Request> findByRequesterIdAndId(Long requesterId, Long requestId);

    List<Request> findByEventId(Long eventId);

    @Query("""
            select r.eventId as eventId,
                   count(r.id) as cnt
            from Request r
            where r.eventId in :eventIds
              and r.status = :status
            group by r.eventId
            """)
    List<ConfirmedCount> countByEventIdsAndStatus(@Param("eventIds") List<Long> eventIds,
                                                  @Param("status") RequestStatus status);

    Integer countByEventIdAndStatus(Long eventId, RequestStatus status);

    boolean existsByRequesterIdAndEventId(Long requesterId, Long eventId);

    boolean existsByRequesterIdAndEventIdAndStatus(
            Long requesterId,
            Long eventId,
            RequestStatus status
    );

}