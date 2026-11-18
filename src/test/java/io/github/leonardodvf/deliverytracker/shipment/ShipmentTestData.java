package io.github.leonardodvf.deliverytracker.shipment;

import io.github.leonardodvf.deliverytracker.shipment.dto.AddressDto;
import io.github.leonardodvf.deliverytracker.shipment.dto.CreateShipmentRequest;
import java.math.BigDecimal;

public final class ShipmentTestData {

    private ShipmentTestData() {
    }

    public static CreateShipmentRequest request() {
        return new CreateShipmentRequest(
                new AddressDto("Sender", "20000000", "Origin street", "10", null, "Center", "Rio de Janeiro", "RJ"),
                new AddressDto("Recipient", "01000000", "Destination street", "20", "Apt 1", "Center", "São Paulo", "SP"),
                new BigDecimal("2"), new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("10"));
    }
}
