package edu.cit.franza;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Single Spring Boot entry point for the whole application.
 * Sitting in the parent package (edu.cit.franza) lets component scanning
 * pick up both edu.cit.franza.shop (Order module) and
 * edu.cit.franza.inventory (Inventory module) without any extra config.
 */
@SpringBootApplication
public class ShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }
}
