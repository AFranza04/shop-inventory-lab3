package edu.cit.franza.config;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InstanceContext {

    private final String instanceId = UUID.randomUUID().toString();
    private final Instant startedAt = Instant.now();

    public String getInstanceId() {
        return instanceId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public long getUptimeSeconds() {
        return java.time.Duration.between(startedAt, Instant.now()).getSeconds();
    }
}