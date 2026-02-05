package ru.practicum.analyzer.model;


import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;


@Entity
@Table(name = "events_similarity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventSimilarity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id;
    @Column(name = "eventA_id", nullable = false)
    long eventA;
    @Column(name = "eventB_id", nullable = false)
    long eventB;
    @Column(name = "score", nullable = false)
    double score;
    @Column(name = "timestamp_at", nullable = false)
    Instant timestamp;
}