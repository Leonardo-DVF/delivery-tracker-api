package io.github.leonardodvf.deliverytracker.webhook.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierWebhookPropertiesTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CarrierWebhookConfig.class);

    @Test
    void bindsSecretAndDefaultsAndHidesTheSecretInToString() {
        runner.withPropertyValues("app.carrier.webhook.secret=" + "s".repeat(32), "app.carrier.webhook.tolerance=PT5M")
                .run(context -> {
                    var properties = context.getBean(CarrierWebhookProperties.class);
                    assertThat(properties.tolerance()).isEqualTo(Duration.ofMinutes(5));
                    assertThat(properties.toString()).doesNotContain("sss").contains("****");
                });
    }

    @Test
    void refusesToStartWithoutASecret() {
        runner.withPropertyValues("app.carrier.webhook.tolerance=PT5M")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.carrier.webhook.secret=", "app.carrier.webhook.tolerance=PT5M")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesShortSecretsAndUnreasonableTolerances() {
        runner.withPropertyValues("app.carrier.webhook.secret=" + "s".repeat(31), "app.carrier.webhook.tolerance=PT5M")
                .run(context -> assertThat(context).hasFailed());
        for (var tolerance : new String[]{"PT29S", "PT16M"}) {
            runner.withPropertyValues("app.carrier.webhook.secret=" + "s".repeat(32), "app.carrier.webhook.tolerance=" + tolerance)
                    .run(context -> assertThat(context).hasFailed());
        }
    }
}
