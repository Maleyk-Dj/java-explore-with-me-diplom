package ru.practicum.analyzer.config;

import lombok.Setter;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import java.util.Properties;

@ConfigurationProperties(value = "analyzer.kafka")
@Setter
@Configuration
public class AnalyzerKafkaConfig {
    private String bootstrapServer;
    private String keyDeserializer;
    private String eventDeserializer;
    private String userDeserializer;
    private String eventGroupId;
    private String userGroupId;
    private String maxPollRecords;
    private String maxPollIntervalMs;
    private String fetchMaxWaitMs;
    private String enableAutoCommit;

    @Bean
    public Consumer<Long, SpecificRecordBase> eventConsumer() {
        Properties config = new Properties();
        config.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
        config.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer);
        config.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, eventDeserializer);
        config.setProperty(ConsumerConfig.GROUP_ID_CONFIG, eventGroupId);
        config.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        config.setProperty(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);
        config.setProperty(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, fetchMaxWaitMs);
        config.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommit);
        return new KafkaConsumer<>(config);
    }

    @Bean
    public Consumer<Long, SpecificRecordBase> userConsumer() {
        Properties config = new Properties();
        config.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
        config.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer);
        config.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, userDeserializer);
        config.setProperty(ConsumerConfig.GROUP_ID_CONFIG, userGroupId);
        config.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        config.setProperty(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);
        config.setProperty(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, fetchMaxWaitMs);
        config.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommit);
        return new KafkaConsumer<>(config);
    }
}