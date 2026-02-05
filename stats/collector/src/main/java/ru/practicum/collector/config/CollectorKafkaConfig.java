package ru.practicum.collector.config;


import lombok.Setter;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
@Setter
@ConfigurationProperties(prefix = "collector.kafka")
public class CollectorKafkaConfig {
    String bootstrapServer;
    String keySerializer;
    String valueSerializer;

    @Bean
    public Producer<Long, SpecificRecordBase> producer() {
        Properties config = new Properties();
        config.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
        config.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer);
        config.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer);
        return new KafkaProducer<>(config);
    }
}