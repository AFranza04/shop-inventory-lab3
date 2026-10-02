package edu.cit.franza.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
class LegacySupplyClient {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyClient.class);
    private final RestTemplate restTemplate;
    private final String baseUrl = "https://legacysupply.onrender.com/api/v1";

    @Value("${LS_CLIENT_ID:23-3267-200}")
    private String clientId;

    @Value("${LS_API_KEY:LSK-D9375FEBAEC2E6E9D356}")
    private String apiKey;

    private String sessionToken;

    LegacySupplyClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    synchronized String getSessionToken() {
        if (sessionToken != null) return sessionToken;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        HttpEntity<AuthRequestXml> req = new HttpEntity<>(new AuthRequestXml(clientId, apiKey), headers);

        try {
            ResponseEntity<AuthResponseXml> resp = restTemplate.postForEntity(
                baseUrl + "/auth/token", req, AuthResponseXml.class
            );
            if (resp.getBody() != null && resp.getBody().sessionToken != null) {
                this.sessionToken = resp.getBody().sessionToken;
                return this.sessionToken;
            }
        } catch (Exception e) {
            log.error("Failed to authenticate with LegacySupply: {}", e.getMessage());
            throw e;
        }
        throw new RuntimeException("Could not retrieve LegacySupply session token");
    }

    synchronized void invalidateSession() {
        this.sessionToken = null;
    }

    void fetchCatalog() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-LS-Session", getSessionToken());
            restTemplate.exchange(baseUrl + "/catalog", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        } catch (Exception e) {
            log.warn("Catalog ping check: {}", e.getMessage());
        }
    }

    PurchaseOrderAckXml submitOrder(PurchaseOrderXml order, String requestId) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < 3) {
            attempts++;
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_XML);
                headers.set("X-LS-Session", getSessionToken());
                headers.set("X-Request-Id", requestId);

                HttpEntity<PurchaseOrderXml> entity = new HttpEntity<>(order, headers);
                ResponseEntity<PurchaseOrderAckXml> resp = restTemplate.postForEntity(
                    baseUrl + "/purchase-orders", entity, PurchaseOrderAckXml.class
                );
                return resp.getBody();
            } catch (HttpStatusCodeException ex) {
                lastException = ex;
                if (ex.getStatusCode().value() == 401) {
                    invalidateSession();
                } else if (ex.getStatusCode().value() == 409) {
                    // Safe replay idempotent response
                    return null;
                }
            } catch (Exception ex) {
                lastException = ex;
            }

            try {
                Thread.sleep(1000L * attempts);
            } catch (InterruptedException ignored) {}
        }
        throw new RuntimeException("LegacySupply order submission failed after 3 attempts", lastException);
    }

    PurchaseOrderStatusXml checkStatus(String poNumber) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-LS-Session", getSessionToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<PurchaseOrderStatusXml> resp = restTemplate.exchange(
                baseUrl + "/purchase-orders/" + poNumber, HttpMethod.GET, entity, PurchaseOrderStatusXml.class
            );
            return resp.getBody();
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 401) {
                invalidateSession();
            }
            return null;
        }
    }
}