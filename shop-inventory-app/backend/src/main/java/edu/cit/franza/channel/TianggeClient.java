package edu.cit.franza.channel;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import edu.cit.franza.config.InstanceContext;

@Component
class TianggeClient {

    private static final Logger log = LoggerFactory.getLogger(TianggeClient.class);

    private final RestClient restClient;
    private final String clientId;
    private final String apiKey;
    private final InstanceContext instanceContext;

    TianggeClient(
        @Value("${app.tiangge.base-url:https://legacysupply.onrender.com/tiangge/v1}") String baseUrl,
        @Value("${LS_CLIENT_ID:23-3267-200}") String clientId,
        @Value("${LS_API_KEY:LSK-D9375FEBAEC2E6E9D356}") String apiKey,
        InstanceContext instanceContext
    ) {
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.instanceContext = instanceContext;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    private RestClient.RequestHeadersSpec<?> withHeaders(RestClient.RequestHeadersSpec<?> spec) {
        return spec
            .header("X-Client-Id", clientId)
            .header("Authorization", "Bearer " + apiKey)
            .header("X-Client-Instance", instanceContext.getInstanceId());
    }

    // --- Task 1: Heartbeat ---
    record HeartbeatRequest(String appName, String startedAt, long uptimeSeconds) {}
    record HeartbeatResponse(String serverTime, int nextHeartbeatSeconds) {}

    public HeartbeatResponse sendHeartbeat() {
        HeartbeatRequest req = new HeartbeatRequest(
            "franza-shop",
            instanceContext.getStartedAt().toString(),
            instanceContext.getUptimeSeconds()
        );

        return withHeaders(
            restClient.post()
                .uri("/instances/heartbeat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
        ).retrieve().body(HeartbeatResponse.class);
    }

    // --- Task 2: Listings ---
    record ListingItem(String sellerSku, String title, String supplierSku) {}

    public void publishListings(List<ListingItem> listings) {
        withHeaders(
            restClient.put()
                .uri("/listings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(listings)
        ).retrieve().toBodilessEntity();
    }

    // --- Task 3: Stock ---
    record StockItem(String sellerSku, int available) {}

    public void publishStock(List<StockItem> stockList) {
        withHeaders(
            restClient.put()
                .uri("/stock")
                .contentType(MediaType.APPLICATION_JSON)
                .body(stockList)
        ).retrieve().toBodilessEntity();
    }

    // --- Feed DTOs ---
    record FeedLine(String sellerSku, int qty) {}
    record FeedBuyer(String name, String city) {}
    record FeedEvent(
        long seq,
        String eventId,
        String type,
        String orderId,
        String placedAt,
        String decisionDeadline,
        String cancelledAt,
        String confirmDeadline,
        List<FeedLine> lines,
        FeedBuyer buyer
    ) {}
    record FeedResponse(List<FeedEvent> events, Long nextCursor) {}

    public FeedResponse getFeed(long afterCursor, int limit) {
        return withHeaders(
            restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/feed")
                    .queryParam("after", afterCursor)
                    .queryParam("limit", limit)
                    .build())
        ).retrieve().body(FeedResponse.class);
    }

    // --- Task 4: Decisions ---
    record DecisionRequest(String decision, String shopOrderId, String reason) {}

    public void sendDecision(String orderId, String decision, String shopOrderId, String reason) {
        DecisionRequest req = new DecisionRequest(decision, shopOrderId, reason);
        withHeaders(
            restClient.post()
                .uri("/orders/{orderId}/decision", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
        ).retrieve().toBodilessEntity();
    }

    // --- Task 6: Backorder Resolution ---
    record ResolutionRequest(String status) {}

    public void sendResolution(String orderId, String status) {
        ResolutionRequest req = new ResolutionRequest(status);
        withHeaders(
            restClient.post()
                .uri("/orders/{orderId}/resolution", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
        ).retrieve().toBodilessEntity();
    }

    // --- Task 5: Customer Cancellations ---
    record CancellationConfirmRequest(boolean restocked) {}

    public void confirmCancellation(String orderId) {
        CancellationConfirmRequest req = new CancellationConfirmRequest(true);
        withHeaders(
            restClient.post()
                .uri("/orders/{orderId}/cancellation", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
        ).retrieve().toBodilessEntity();
    }
}