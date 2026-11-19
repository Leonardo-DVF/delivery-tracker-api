package io.github.leonardodvf.deliverytracker.webhook.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CarrierWebhookProperties.class)
public class CarrierWebhookConfig {
}
