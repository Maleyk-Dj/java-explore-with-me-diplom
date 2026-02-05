package ru.practicum.analyzer.service;

import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.analyzer.mapper.AnalyzerMapper;
import ru.practicum.analyzer.model.EventSimilarity;
import ru.practicum.analyzer.model.UserAction;
import ru.practicum.analyzer.repositories.EventSimilarityRepository;
import ru.practicum.analyzer.repositories.UserActionRepository;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.grpc.stats.analyzer.InteractionsCountRequestProto;
import ru.practicum.grpc.stats.analyzer.RecommendedEventProto;
import ru.practicum.grpc.stats.analyzer.SimilarEventsRequestProto;
import ru.practicum.grpc.stats.analyzer.UserPredictionsRequestProto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyzerServiceImpl implements AnalyzerService {
    private final EventSimilarityRepository eventSimilarityRepository;
    private final UserActionRepository userActionRepository;

    @Override
    @Transactional
    public void saveEventSimilarity(EventSimilarityAvro eventSimilarity) {
        Optional<EventSimilarity> oldEventSimilarity = eventSimilarityRepository.findByEventAAndEventB(
                eventSimilarity.getEventA(), eventSimilarity.getEventB());

        if (oldEventSimilarity.isEmpty()) {
            eventSimilarityRepository.save(AnalyzerMapper.mapToEventSimilarity(eventSimilarity));
            log.info("Сохранение нового сходства событий " + eventSimilarity);
        } else {
            EventSimilarity updatedEventSimilarity = oldEventSimilarity.get();
            updatedEventSimilarity.setScore(eventSimilarity.getScore());
            updatedEventSimilarity.setTimestamp(eventSimilarity.getTimestamp());
            log.info("Обновление нового сходства событий" + eventSimilarity);
        }
    }

    @Override
    @Transactional
    public void saveUserAction(UserActionAvro userAction) {
        Optional<UserAction> oldUserAction = userActionRepository.findByUserIdAndEventId(userAction.getUserId(),
                userAction.getEventId());

        UserAction newUserAction = AnalyzerMapper.mapToUserAction(userAction);
        if (oldUserAction.isEmpty()) {
            userActionRepository.save(newUserAction);
            log.info("Сохранение новых действий пользователя " + newUserAction);
        } else {
            UserAction updatedUserAction = oldUserAction.get();
            if (updatedUserAction.getWeight() < newUserAction.getWeight()) { // под вопросом
                updatedUserAction.setWeight(newUserAction.getWeight());
                updatedUserAction.setTimestamp(newUserAction.getTimestamp());
                log.info("Обновление действий пользователя " + newUserAction);
            }
        }
    }

    @Override
    public List<RecommendedEventProto> getSimilarEvents(SimilarEventsRequestProto request) {
        long eventId = request.getEventId();
        long userId = request.getUserId();
        long max = request.getMaxResults();

        List<EventSimilarity> eventSimilarities = eventSimilarityRepository.GetSimilarEvents(eventId, userId, max);

        return eventSimilarities.stream()
                .map(eventSimilarity -> AnalyzerMapper.mapRecommendedEventProto(eventSimilarity, eventId))
                .toList();
    }

    @Override
    public List<RecommendedEventProto> getRecommendationsForUser(UserPredictionsRequestProto request) {
        long userId = request.getUserId();
        long max = request.getMaxResults();

        List<UserAction> userActions = userActionRepository.findAllByUserIdWithLimit(userId, max); // получил сущности UserAction

        if (userActions.isEmpty()) {
            return List.of();
        }

        List<Long> viewedEventIds = userActions.stream() // список событий с которыми взаимодействовал пользователь
                .map(UserAction::getEventId)
                .toList();

        List<EventSimilarity> eventSimilarities = eventSimilarityRepository.findSimilarNotViewedByUser(viewedEventIds, // список событий с большим сходством с которым не взаимодействовал пользователь
                userId, max);

        List<EventSimilarity> similarEvents = eventSimilarities.stream() // список событий с наибольшими оценками впереди ограниченные max с которым не взаимодействовал пользователь
                .sorted(Comparator.comparing(EventSimilarity::getScore).reversed()).limit(max).toList();

        List<Long> similarEventsIds = similarEvents.stream()
                .map(similarEvent -> viewedEventIds.contains(similarEvent.getEventA()) ?
                        similarEvent.getEventB() : similarEvent.getEventA())
                .toList();

        Map<Long, Double> rating = predictRatings(similarEventsIds, userId);
        return toRecommendedEventProtos(rating, max);
    }

    @Override
    public List<RecommendedEventProto> getInteractionsCount(InteractionsCountRequestProto request) {
        List<Long> eventIds = request.getEventIdList();

        List<Tuple> listObject = userActionRepository.findSumMaxWeight(eventIds);

        Map<Long, Double> weightOfEvents = listObject.stream()
                .collect(Collectors.toMap(x -> (Long) x.get(0), x -> (Double) x.get(1)));

        return eventIds.stream()
                .map(eventId -> AnalyzerMapper.mapRecommendedEventProto(
                        eventId,
                        weightOfEvents.getOrDefault(eventId, 0.0)
                ))
                .collect(Collectors.toList());
    }

    public Map<Long, Double> predictRatings(List<Long> candidateEventIds, long userId) {

        Map<Long, List<EventSimilarity>> grouped = groupViewedNeighbors(candidateEventIds, userId); // получаем связи с просмотренными соседями

        Set<Long> neighborEventIds = new HashSet<>(); // собираем всех соседей с просмотренными событиями
        for (List<EventSimilarity> similarities : grouped.values()) {
            for (EventSimilarity es : similarities) {
                long neighbor = candidateEventIds.contains(es.getEventA()) ? es.getEventB() : es.getEventA();
                neighborEventIds.add(neighbor);
            }
        }

        if (neighborEventIds.isEmpty()) {
            return candidateEventIds.stream().collect(Collectors.toMap(id -> id, id -> 0.0));
        }

        List<UserAction> userActions = userActionRepository.findByUserIdAndEventIdIn(userId, // загружаем UserAction для всех соседей
                new ArrayList<>(neighborEventIds));
        Map<Long, Double> userWeights = userActions.stream()
                .collect(Collectors.toMap(UserAction::getEventId, UserAction::getWeight));

        Map<Long, Double> predictedRatings = new LinkedHashMap<>(); // вычисляем предсказанные оценки

        for (Long candidateId : candidateEventIds) {
            List<EventSimilarity> similarities = grouped.getOrDefault(candidateId, Collections.emptyList());

            double weightedSum = 0.0;

            for (EventSimilarity es : similarities) {
                long neighborId = (es.getEventA() == candidateId) ? es.getEventB() : es.getEventA();
                Double weight = userWeights.get(neighborId);

                double score = es.getScore();
                weightedSum += score * weight;
            }
            predictedRatings.put(candidateId, weightedSum);
        }
        return predictedRatings;
    }

    public Map<Long, List<EventSimilarity>> groupViewedNeighbors(List<Long> eventIds, long userId) {

        List<EventSimilarity> similarities = eventSimilarityRepository.findViewedNeighbors(eventIds, userId); // получаем все подходящие связи

        Map<Long, List<EventSimilarity>> grouped = new LinkedHashMap<>(); // группируем просмотренных соседей, сохраняя порядок

        for (Long eventId : eventIds) {
            grouped.put(eventId, new ArrayList<>());
        }

        for (EventSimilarity es : similarities) { // распределяем сходства
            if (eventIds.contains(es.getEventA())) {
                grouped.get(es.getEventA()).add(es);
            } else if (eventIds.contains(es.getEventB())) {
                grouped.get(es.getEventB()).add(es);
            }
        }
        return grouped;
    }

    public List<RecommendedEventProto> toRecommendedEventProtos(Map<Long, Double> predictedRatings, long maxResults) {

        return predictedRatings.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(maxResults)
                .map(entry -> RecommendedEventProto.newBuilder()
                        .setEventId(entry.getKey())
                        .setScore(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }
}