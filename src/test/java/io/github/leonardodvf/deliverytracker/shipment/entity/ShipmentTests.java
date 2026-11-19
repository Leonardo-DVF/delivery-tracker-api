package io.github.leonardodvf.deliverytracker.shipment.entity;

import io.github.leonardodvf.deliverytracker.shipment.exception.InvalidStatusTransitionException;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import java.time.Instant;

import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.QUOTED_AT;
import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.shipment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShipmentTests {

    private final User customer = new User("Owner", "owner@example.test", "hash", UserRole.CUSTOMER);

    @Test
    void walksTheWholeLifecycleUpdatingTheTimestamp() {
        var shipment = shipment(customer, QUOTED_AT);
        Instant at = QUOTED_AT;
        for (var next : new ShipmentStatus[]{ShipmentStatus.PICKED_UP, ShipmentStatus.IN_TRANSIT,
                ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.DELIVERED}) {
            at = at.plusSeconds(60);
            shipment.transitionTo(next, at);
            assertThat(shipment.getStatus()).isEqualTo(next);
            assertThat(shipment.getUpdatedAt()).isEqualTo(at);
        }
        assertThat(shipment.getCreatedAt()).isEqualTo(QUOTED_AT);
    }

    @Test
    void rejectsInvalidTransitionWithoutChangingState() {
        var shipment = shipment(customer, QUOTED_AT);

        assertThatThrownBy(() -> shipment.transitionTo(ShipmentStatus.DELIVERED, QUOTED_AT.plusSeconds(60)))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .satisfies(ex -> {
                    var problem = ((InvalidStatusTransitionException) ex).getBody();
                    assertThat(problem.getStatus()).isEqualTo(409);
                    assertThat(problem.getDetail()).isEqualTo("Cannot change shipment status from CREATED to DELIVERED");
                    assertThat(problem.getProperties()).containsEntry("currentStatus", ShipmentStatus.CREATED)
                            .containsEntry("requestedStatus", ShipmentStatus.DELIVERED);
                });
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(shipment.getUpdatedAt()).isEqualTo(QUOTED_AT);
    }

    @Test
    void rejectsAnyChangeAfterAFinalStatus() {
        var shipment = shipment(customer, QUOTED_AT);
        shipment.transitionTo(ShipmentStatus.CANCELED, QUOTED_AT.plusSeconds(60));

        assertThatThrownBy(() -> shipment.transitionTo(ShipmentStatus.PICKED_UP, QUOTED_AT.plusSeconds(120)))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> shipment.transitionTo(ShipmentStatus.CANCELED, QUOTED_AT.plusSeconds(120)))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void rejectsAChangeThatPredatesTheLastUpdate() {
        var shipment = shipment(customer, QUOTED_AT);

        assertThatThrownBy(() -> shipment.transitionTo(ShipmentStatus.PICKED_UP, QUOTED_AT.minusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CREATED);
    }
}
