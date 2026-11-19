package io.github.leonardodvf.deliverytracker.shipment.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class ShipmentStatusTests {

    private static final Map<ShipmentStatus, Set<ShipmentStatus>> ALLOWED = Map.of(
            CREATED, EnumSet.of(PICKED_UP, CANCELED),
            PICKED_UP, EnumSet.of(IN_TRANSIT, CANCELED),
            IN_TRANSIT, EnumSet.of(OUT_FOR_DELIVERY, CANCELED),
            OUT_FOR_DELIVERY, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.noneOf(ShipmentStatus.class),
            CANCELED, EnumSet.noneOf(ShipmentStatus.class));

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyPair")
    void allowsOnlyTheDocumentedTransitions(ShipmentStatus from, ShipmentStatus to) {
        assertThat(from.canTransitionTo(to)).isEqualTo(ALLOWED.get(from).contains(to));
    }

    static Stream<Arguments> everyPair() {
        return Arrays.stream(values()).flatMap(from -> Arrays.stream(values()).map(to -> Arguments.of(from, to)));
    }

    @Test
    void coversEveryStatus() {
        assertThat(ALLOWED.keySet()).containsExactlyInAnyOrder(values());
    }

    @Test
    void onlyDeliveredAndCanceledAreFinal() {
        assertThat(Arrays.stream(values()).filter(ShipmentStatus::isFinal)).containsExactlyInAnyOrder(DELIVERED, CANCELED);
        assertThat(Arrays.stream(values()).filter(ShipmentStatus::isFinal))
                .allSatisfy(status -> assertThat(ALLOWED.get(status)).isEmpty());
    }
}
