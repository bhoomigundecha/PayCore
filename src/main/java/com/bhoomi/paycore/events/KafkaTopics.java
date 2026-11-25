package com.bhoomi.paycore.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Spring's KafkaAdmin creates this topic at startup if it doesn't exist yet. */
@Configuration
public class KafkaTopics {

    @Bean
    NewTopic paymentEventsTopic(@Value("${paycore.events.topic}") String topic) {
        // 3 partitions: events are keyed by payment id, so each payment's events stay in order
        // while different payments can be processed in parallel.
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
