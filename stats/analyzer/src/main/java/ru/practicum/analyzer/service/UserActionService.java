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
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserActionService implements Runnable {
    private final Consumer<Long, SpecificRecordBase> userConsumer;
    @Value("${analyzer.kafka.topic-user-actions}")
    private String topic;
    @Value("${analyzer.kafka.consume-attempt-timeout}")
    private long consumeAttemptTimeout;
    private static final Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();
    private final AnalyzerServiceImpl analyzerService;

    @Override
    public void run() {
        try {
            userConsumer.subscribe(List.of(topic));
            Runtime.getRuntime().addShutdownHook(new Thread(userConsumer::wakeup));

            int count = 0;
            while (true) {
                ConsumerRecords<Long, SpecificRecordBase> consumerRecords = userConsumer.poll(
                        Duration.ofMillis(consumeAttemptTimeout));
                for (ConsumerRecord<Long, SpecificRecordBase> record : consumerRecords) {
                    UserActionAvro userAction = (UserActionAvro) record.value();
                    log.info("Получено действие пользователя от Collector {}, {}, {}, {}",
                            userAction.getUserId(), userAction.getEventId(), userAction.getActionType(),
                            userAction.getTimestamp());

                    manageOffsets(record, count);
                    count++;
                    analyzerService.saveUserAction(userAction);
                }
                userConsumer.commitAsync();
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Произошла ошибка при обработке событий от пользователей ", e);
        } finally {
            try {
                userConsumer.commitSync(currentOffsets);
            } finally {
                log.info("Закрытие consumer");
                userConsumer.close();
            }
        }
    }

    private void manageOffsets(ConsumerRecord<Long, SpecificRecordBase> record, int count) {
        currentOffsets.put(
                new TopicPartition(record.topic(), record.partition()),
                new OffsetAndMetadata(record.offset() + 1)
        );

        if (count % 10 == 0) {
            userConsumer.commitAsync(currentOffsets, (offsets, exception) -> {
                if (exception != null) {
                    log.warn("Ошибка при фиксации смещения : {}", offsets, exception);
                }
            });
        }
    }
}