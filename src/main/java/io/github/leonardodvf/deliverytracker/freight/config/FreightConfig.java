package io.github.leonardodvf.deliverytracker.freight.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FreightProperties.class)
public class FreightConfig {
}
