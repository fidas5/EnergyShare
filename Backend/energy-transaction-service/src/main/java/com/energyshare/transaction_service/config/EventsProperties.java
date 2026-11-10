package com.energyshare.transaction_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.events")
public record EventsProperties(String exchange, String queue, String confirmedKey, String cancelledKey) {
}
