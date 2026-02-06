package ru.practicum.aggregator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AggregationStarter {
    private final Consumer<Long, SpecificRecordBase> consumer;
    @Value("${aggregator.kafka.topic-user-actions}")
    private String topicUserActions;
    private final Producer<Long, SpecificRecordBase> producer;
    @Value("${aggregator.kafka.topic-events-similarity}")
    private String topicEventsSimilarity;
    @Value(value = "${aggregator.kafka.consume-attempt-timeout}")
    private long consumeAttemptTimeout;
    private static final Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();

    Map<Long, Map<Long, Double>> userActionWeightMatrix = new HashMap<>(); // матрица весов действий пользователей
    Map<Long, Map<Long, Double>> minWeightPairSums = new HashMap<>(); // минимальная сумма для пар мероприятий
    Map<Long, Double> eventWeightSums = new HashMap<>(); // общие суммы весов каждого мероприятия


    public void start() {
        try {
            consumer.subscribe(List.of(topicUserActions));
            Runtime.getRuntime().addShutdownHook(new Thread(consumer::wakeup));

            while (true) {
                ConsumerRecords<Long, SpecificRecordBase> records = consumer.poll(
                        Duration.ofMillis(consumeAttemptTimeout));

                int count = 0;
                for (ConsumerRecord<Long, SpecificRecordBase> record : records) {

                    UserActionAvro userAction = (UserActionAvro) record.value();
                    log.info("Получено действие пользователя от Collector {}, {}, {}, {}",
                            userAction.getUserId(), userAction.getEventId(), userAction.getActionType(),
                            userAction.getTimestamp());

                    manageOffsets(record, count);
                    count++;
                    aggregator(userAction);
                }
                consumer.commitAsync();
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Произошла ошибка при обработке событий от пользователей ", e);
        } finally {
            try {
                producer.flush();
                consumer.commitSync(currentOffsets);
            } finally {
                log.info("Закрытие consumer");
                consumer.close();
                log.info("Закрытие producer");
                producer.close();
            }
        }
    }

    private void manageOffsets(ConsumerRecord<Long, SpecificRecordBase> record, int count) {
        currentOffsets.put(
                new TopicPartition(record.topic(), record.partition()),
                new OffsetAndMetadata(record.offset() + 1)
        );

        if (count % 10 == 0) {
            consumer.commitAsync(currentOffsets, (offsets, exception) -> {
                if (exception != null) {
                    log.warn("Ошибка при фиксации смещения : {}", offsets, exception);
                }
            });
        }
    }

    private void aggregator(UserActionAvro userAction) {
        long eventA = userAction.getEventId();
        long userId = userAction.getUserId();
        double weight = calculateWeightAction(userAction.getActionType());

        userActionWeightMatrix.putIfAbsent(eventA, new HashMap<>());
        eventWeightSums.putIfAbsent(eventA, 0.0);

        List<Long> eventIds = userActionWeightMatrix.keySet() // получаем список мероприятий для сравнения, за исключением нашего
                .stream()
                .filter(event -> event != eventA)
                .toList();

        Double currentUserWeight = userActionWeightMatrix.get(eventA).get(userId); // получаем текущий вес пользователя для этого мероприятия ,если есть

        if (currentUserWeight != null) { // если пользователь уже участвовал в этом мероприятия
            if (currentUserWeight >= weight) {
                return; // вес не увеличился
            }

            double oldWeight = currentUserWeight; // меняем вес мероприятия
            userActionWeightMatrix.get(eventA).put(userId, weight);
            eventWeightSums.put(eventA, eventWeightSums.get(eventA) - oldWeight + weight);
        } else {
            userActionWeightMatrix.get(eventA).put(userId, weight); // пользователь впервые участвует в этом мероприятии
            eventWeightSums.put(eventA, eventWeightSums.get(eventA) + weight);
        }


        if (!eventIds.isEmpty()) { // считаем все попарные коэффициенты только если есть другие мероприятия
            calculateAndSendSimilarities(eventA, userId, eventIds);
        }
    }

    private void calculateAndSendSimilarities(long eventA, long userId, List<Long> eventIds) {
        for (Long eventB : eventIds) {
            if (!userActionWeightMatrix.containsKey(eventB) ||
                    !userActionWeightMatrix.get(eventA).containsKey(userId) ||
                    !userActionWeightMatrix.get(eventB).containsKey(userId)) {
                continue;
            }

            double sumOfMinWeights = sumOfMinWeightsForEventPair(userActionWeightMatrix, eventA, eventB); // считаем минимальную сумму пар для мероприятия

            putIntoMinWeightPairSums(eventA, eventB, sumOfMinWeights); // записываем минимальную сумму для пар мероприятий

            double eventWeightSumA = eventWeightSums.get(eventA);
            double eventWeightSumB = eventWeightSums.get(eventB);

            if (eventWeightSumA <= 0 || eventWeightSumB <= 0) { // проверяем, что суммы весов положительные
                continue;
            }

            double sumOfProductsOfSquareRoots = Math.sqrt(eventWeightSumA) * Math.sqrt(eventWeightSumB); // считаем знаменатель

            double coefficient = sumOfProductsOfSquareRoots > 0 ? sumOfMinWeights / sumOfProductsOfSquareRoots : 0.0; // получаем коэф, проверяем деление на ноль

            EventSimilarityAvro eventSimilarity = createEventSimilarityAvro(eventA, eventB, coefficient);
            sendRecord(userId, eventSimilarity);
            log.info("Коэффициент сходства мероприятий отправлен в сервис Analyzer {}", eventSimilarity);
        }
    }

    private static Double calculateWeightAction(ActionTypeAvro action) {
        return switch (action) {
            case VIEW -> 0.4;
            case REGISTER -> 0.8;
            case LIKE -> 1.0;
        };
    }

    private double sumOfMinWeightsForEventPair(Map<Long, Map<Long, Double>> userActionWeightMatrix, long eventA,
                                               long eventB) {
        Map<Long, Double> userWeightEventA = userActionWeightMatrix.get(eventA);
        Map<Long, Double> userWeightEventB = userActionWeightMatrix.get(eventB);

        if (userWeightEventA == null || userWeightEventB == null || userWeightEventA.isEmpty() ||
                userWeightEventB.isEmpty()) {
            return 0.0;
        }

        Set<Long> setUserEventA = new HashSet<>(userWeightEventA.keySet()); // поиск общих пользователей двух событий
        Set<Long> setUserEventB = new HashSet<>(userWeightEventB.keySet());

        setUserEventA.retainAll(setUserEventB); // пересечение общих пользователей

        if (setUserEventA.isEmpty()) { // если нет общих пользователей
            return 0.0;
        }

        return setUserEventA.stream()
                .mapToDouble(user -> Math.min(userWeightEventA.get(user), userWeightEventB.get(user)))
                .sum();
    }

    private void putIntoMinWeightPairSums(long eventA, long eventB, double sum) {
        long first = Math.min(eventA, eventB);
        long second = Math.max(eventA, eventB);

        minWeightPairSums
                .computeIfAbsent(first, e -> new HashMap<>())
                .put(second, sum);
    }

    private EventSimilarityAvro createEventSimilarityAvro(long eventA, long eventB, double coefficient) {
        long first = Math.min(eventA, eventB);
        long second = Math.max(eventA, eventB);

        return EventSimilarityAvro.newBuilder()
                .setEventA(first)
                .setEventB(second)
                .setScore(coefficient)
                .setTimestamp(Instant.now())
                .build();
    }

    private void sendRecord(long userId, EventSimilarityAvro eventSimilarity) {
        if (eventSimilarity == null) {
            log.warn("Попытка отправить null EventSimilarityAvro");
            return;
        }
        ProducerRecord<Long, SpecificRecordBase> producerRecord = new ProducerRecord<>(topicEventsSimilarity,
                null,
                Instant.now().toEpochMilli(),
                userId,
                eventSimilarity
        );
        producer.send(producerRecord);
    }
}