package ru.practicum.analyzer.service;

import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.grpc.stats.analyzer.InteractionsCountRequestProto;
import ru.practicum.grpc.stats.analyzer.RecommendedEventProto;
import ru.practicum.grpc.stats.analyzer.SimilarEventsRequestProto;
import ru.practicum.grpc.stats.analyzer.UserPredictionsRequestProto;

import java.util.List;

public interface AnalyzerService {
    void saveEventSimilarity(EventSimilarityAvro eventSimilarityAvro);

    void saveUserAction(UserActionAvro userAction);

    List<RecommendedEventProto> getSimilarEvents(SimilarEventsRequestProto request);

    List<RecommendedEventProto> getRecommendationsForUser(UserPredictionsRequestProto request);

    List<RecommendedEventProto> getInteractionsCount(InteractionsCountRequestProto request);
}