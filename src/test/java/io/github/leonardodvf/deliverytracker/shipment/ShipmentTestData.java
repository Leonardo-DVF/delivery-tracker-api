package io.github.leonardodvf.deliverytracker.shipment;

import io.github.leonardodvf.deliverytracker.freight.entity.FreightQuote;
import io.github.leonardodvf.deliverytracker.shipment.dto.AddressDto;
import io.github.leonardodvf.deliverytracker.shipment.dto.CreateShipmentRequest;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class ShipmentTestData {

    public static final Instant QUOTED_AT = Instant.parse("2026-11-18T12:00:00Z");

    private ShipmentTestData() {
    }

    public static CreateShipmentRequest request() {
        return request(UUID.randomUUID());
    }

    public static CreateShipmentRequest request(UUID quoteId) {
        return new CreateShipmentRequest(quoteId,
                new AddressDto("Sender", "20000000", "Origin street", "10", null, "Center", "Rio de Janeiro", "RJ"),
                new AddressDto("Recipient", "01000000", "Destination street", "20", "Apt 1", "Center", "São Paulo", "SP"),
                new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("10"));
    }

    public static FreightQuote quote(User requester) {
        return quote(requester, "01000000", QUOTED_AT);
    }

    public static FreightQuote quote(User requester, String destinationPostalCode, Instant createdAt) {
        return new FreightQuote(requester, destinationPostalCode, new BigDecimal("2.000"), new BigDecimal("15.00"),
                createdAt, Duration.ofMinutes(30));
    }
}
