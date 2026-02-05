package ru.practicum.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventSimilarityService {
    private final Consumer<Long, SpecificRecordBase> eventConsumer;
    @Value("${analyzer.kafka.topic-events-similarity}")
    private String topic;
    @Value("${analyzer.kafka.consume-attempt-timeout}")
    private long consumeAttemptTimeout;
    private static final Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();
    private final AnalyzerService analyzerService;

    public void start() {
        try {
            eventConsumer.subscribe(List.of(topic));
            Runtime.getRuntime().addShutdownHook(new Thread(eventConsumer::wakeup));

            int count = 0;
            while (true) {
                ConsumerRecords<Long, SpecificRecordBase> consumerRecords = eventConsumer.poll(
                        Duration.ofMillis(consumeAttemptTimeout));
                for (ConsumerRecord<Long, SpecificRecordBase> record : consumerRecords) {
                    EventSimilarityAvro eventSimilarityAvro = (EventSimilarityAvro) record.value();
                    log.info("Получен коэффициент сходства мероприятий от Aggregator {}, {}, {}, {}",
                            eventSimilarityAvro.getEventA(), eventSimilarityAvro.getEventB(),
                            eventSimilarityAvro.getScore(), eventSimilarityAvro.getTimestamp());
                    manageOffsets(record, count);
                    count++;

                    analyzerService.saveEventSimilarity(eventSimilarityAvro);
                }
                eventConsumer.commitAsync();
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Произошла ошибка при обработке коэффициентов сходства ", e);
        } finally {
            try {
                eventConsumer.commitSync(currentOffsets);
            } finally {
                log.info("Закрытие consumer");
                eventConsumer.close();
            }
        }
    }

    private void manageOffsets(ConsumerRecord<Long, SpecificRecordBase> record, int count) {
        currentOffsets.put(
                new TopicPartition(record.topic(), record.partition()),
                new OffsetAndMetadata(record.offset() + 1)
        );

        if (count % 10 == 0) {
            eventConsumer.commitAsync(currentOffsets, (offsets, exception) -> {
                if (exception != null) {
                    log.warn("Ошибка при фиксации смещения : {}", offsets, exception);
                }
            });
        }
    }
}