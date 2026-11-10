package com.energyshare.transaction_service.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.marketplace")
public record MarketplaceProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
