package edu.cit.franza.channel;

import java.time.Instant;
import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "channel_feed_cursor")
class ChannelFeedCursor {
    @Id
    private Integer id = 1;

    @Column(name = "last_cursor")
    private Long lastCursor = 0L;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Long getLastCursor() { return lastCursor; }
    public void setLastCursor(Long lastCursor) { this.lastCursor = lastCursor; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

@Repository
interface ChannelFeedCursorRepository extends CrudRepository<ChannelFeedCursor, Integer> {}

@Entity
@Table(name = "channel_processed_events")
class ProcessedEvent {
    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "processed_at")
    private Instant processedAt = Instant.now();

    public ProcessedEvent() {}
    public ProcessedEvent(String eventId, String eventType, String orderId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.orderId = orderId;
        this.processedAt = Instant.now();
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}

@Repository
interface ProcessedEventRepository extends CrudRepository<ProcessedEvent, String> {}

@Entity
@Table(name = "channel_backorders")
class ChannelBackorder {
    @Id
    @Column(name = "order_id")
    private String orderId;

    @Column(name = "product_id")
    private String productId;

    @Column(name = "qty")
    private Integer qty;

    @Column(name = "status")
    private String status = "BACKORDERED";

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public ChannelBackorder() {}
    public ChannelBackorder(String orderId, String productId, Integer qty) {
        this.orderId = orderId;
        this.productId = productId;
        this.qty = qty;
        this.status = "BACKORDERED";
        this.createdAt = Instant.now();
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

@Repository
interface ChannelBackorderRepository extends CrudRepository<ChannelBackorder, String> {
    List<ChannelBackorder> findByProductIdAndStatus(String productId, String status);
}