package ru.practicum.events.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.practicum.category.model.Category;
import ru.practicum.events.model.Event;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    Page<Event> findByInitiatorOrderByIdAsc(Long initiatorId, Pageable pageable);

    Optional<Event> findByInitiatorAndId(Long initiatorId, Long eventId);

    Collection<Event> findByCategory(Category category);

    List<Event> findByInitiatorOrderByIdAsc(Long initiatorId);


    Integer countByCategoryId(Integer categoryId);
}
