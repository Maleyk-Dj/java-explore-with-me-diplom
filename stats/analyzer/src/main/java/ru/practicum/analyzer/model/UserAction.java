package ru.practicum.analyzer.model;


import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_actions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id;
    @Column(name = "user_id", nullable = false)
    long userId;
    @Column(name = "event_id", nullable = false)
    long eventId;
    @Column(nullable = false)
    double weight;
    @Column(name = "timestamp_at", nullable = false)
    Instant timestamp;
}