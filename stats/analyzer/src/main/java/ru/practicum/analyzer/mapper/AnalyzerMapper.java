package ru.practicum.analyzer.mapper;

import org.springframework.stereotype.Component;
import ru.practicum.analyzer.model.EventSimilarity;
import ru.practicum.analyzer.model.UserAction;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.grpc.stats.analyzer.RecommendedEventProto;

@Component
public class AnalyzerMapper {

    public static EventSimilarity mapToEventSimilarity(EventSimilarityAvro eventSimilarity) {
        return EventSimilarity.builder()
                .eventA(eventSimilarity.getEventA())
                .eventB(eventSimilarity.getEventB())
                .score(eventSimilarity.getScore())
                .timestamp(eventSimilarity.getTimestamp())
                .build();
    }

    public static UserAction mapToUserAction(UserActionAvro userActionAvro) {

        double weight = switch (userActionAvro.getActionType()) {
            case VIEW -> 0.4;
            case REGISTER -> 0.8;
            case LIKE -> 1.0;
        };

        return UserAction.builder()
                .userId(userActionAvro.getUserId())
                .eventId(userActionAvro.getEventId())
                .weight(weight)
                .timestamp(userActionAvro.getTimestamp())
                .build();
    }

    public static RecommendedEventProto mapRecommendedEventProto(EventSimilarity eventSimilarity, long eventId) {
        long event = eventSimilarity.getEventA() == eventId ? eventSimilarity.getEventB() : eventSimilarity.getEventA();

        return RecommendedEventProto.newBuilder()
                .setEventId(event)
                .setScore(eventSimilarity.getScore())
                .build();
    }

    public static RecommendedEventProto mapRecommendedEventProto(Long eventId, Double eventWeight) {
        return RecommendedEventProto.newBuilder()
                .setEventId(eventId)
                .setScore(eventWeight)
                .build();
    }
}
