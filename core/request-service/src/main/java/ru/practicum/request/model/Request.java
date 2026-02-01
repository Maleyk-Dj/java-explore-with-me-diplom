package ru.practicum.request.model;


import jakarta.persistence.*;
import lombok.*;
import ru.practicum.dto.requests.RequestStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "requests")
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Пользователь, отправивший запрос
    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    // Событие, на участие в котором сделан запрос
    @Column(name = "event_id", nullable = false)
    private Long eventId;

    // Дата и время создания запроса
    @Column(name = "created", nullable = false)
    private LocalDateTime created;

    // Статус заявки: PENDING, CONFIRMED, REJECTED, CANCELED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;
}