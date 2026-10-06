package edu.cit.franza.channel;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.franza.events.StockChangedEvent;
import edu.cit.franza.inventory.InventoryService;
import edu.cit.franza.inventory.ReservationResult;
import edu.cit.franza.inventory.model.InventoryItem;

public interface ChannelOrderService {
    void fulfillBackordersForProduct(String productId);
}

@Service
class ChannelOrderServiceImpl implements ChannelOrderService {

    private static final Logger log = LoggerFactory.getLogger(ChannelOrderServiceImpl.class);

    private final ChannelBackorderRepository backorderRepo;
    private final TianggeClient client;
    private final InventoryService inventoryService;

    ChannelOrderServiceImpl(
        ChannelBackorderRepository backorderRepo,
        TianggeClient client,
        InventoryService inventoryService
    ) {
        this.backorderRepo = backorderRepo;
        this.client = client;
        this.inventoryService = inventoryService;
    }

    private String toChannelSku(String localProductId) {
        return switch (localProductId) {
            case "P100" -> "PROD-001";
            case "P200" -> "PROD-002";
            case "P300" -> "PROD-003";
            default -> localProductId;
        };
    }

    @EventListener
    public void onStockChanged(StockChangedEvent event) {
        if (event.newQuantity() > 0) {
            fulfillBackordersForProduct(event.productId());
        }
    }

    @Override
    @Transactional
    public synchronized void fulfillBackordersForProduct(String productId) {
        List<ChannelBackorder> pending = backorderRepo.findByProductIdAndStatus(productId, "BACKORDERED");
        if (pending == null || pending.isEmpty()) {
            return;
        }

        for (ChannelBackorder bo : pending) {
            ReservationResult res = inventoryService.reserve(bo.getProductId(), bo.getQty());
            if (!res.success()) {
                break;
            }

            try {
                client.sendResolution(bo.getOrderId(), "ACCEPTED");
                bo.setStatus("RESOLVED");
                backorderRepo.save(bo);
                log.info("Backorder {} RESOLVED and ACCEPTED for {}", bo.getOrderId(), bo.getProductId());

                int currentQty = inventoryService.getItem(bo.getProductId()).map(InventoryItem::getStock).orElse(0);
                String channelSku = toChannelSku(bo.getProductId());
                client.publishStock(List.of(new TianggeClient.StockItem(channelSku, currentQty)));
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
                log.warn("Backorder {} not found on Tiangge (404). Marked DISCARDED.", bo.getOrderId());
                bo.setStatus("DISCARDED");
                backorderRepo.save(bo);
                inventoryService.restock(bo.getProductId(), bo.getQty());
            } catch (Exception ex) {
                log.warn("Failed resolving backorder {}: {}", bo.getOrderId(), ex.getMessage());
                bo.setStatus("FAILED");
                backorderRepo.save(bo);
                inventoryService.restock(bo.getProductId(), bo.getQty());
            }
        }
    }
}