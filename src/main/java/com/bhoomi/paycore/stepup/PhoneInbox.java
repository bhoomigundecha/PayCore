package com.bhoomi.paycore.stepup;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Test-mode stand-in for an SMS gateway: messages go to a per-user inbox in Redis that the web app
 * shows as a phone notification. In production this class would call an SMS or email provider.
 */
@Component
public class PhoneInbox {

    private static final Duration KEEP = Duration.ofHours(1);
    private static final int MAX_MESSAGES = 20;

    private final StringRedisTemplate redis;

    public PhoneInbox(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void deliver(UUID userId, String text) {
        String key = key(userId);
        redis.opsForList().leftPush(key, Instant.now().toEpochMilli() + "|" + UUID.randomUUID() + "|" + text);
        redis.opsForList().trim(key, 0, MAX_MESSAGES - 1);
        redis.expire(key, KEEP);
    }

    public List<Message> read(UUID userId) {
        List<String> raw = redis.opsForList().range(key(userId), 0, MAX_MESSAGES - 1);
        if (raw == null) {
            return List.of();
        }
        return raw.stream().map(line -> {
            String[] parts = line.split("\\|", 3);
            return new Message(parts[1], Instant.ofEpochMilli(Long.parseLong(parts[0])), parts[2]);
        }).toList();
    }

    private static String key(UUID userId) {
        return "inbox:" + userId;
    }

    public record Message(String id, Instant at, String text) {
    }
}
