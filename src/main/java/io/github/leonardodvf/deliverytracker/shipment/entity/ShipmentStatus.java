package io.github.leonardodvf.deliverytracker.shipment.entity;

/**
 * Shipment lifecycle: CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED,
 * with CANCELED reachable until the package is out for delivery. DELIVERED and CANCELED are final.
 */
public enum ShipmentStatus {
    CREATED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, CANCELED;

    /** The switch is exhaustive, so adding a status fails compilation until its transitions are defined. */
    public boolean canTransitionTo(ShipmentStatus next) {
        return switch (this) {
            case CREATED -> next == PICKED_UP || next == CANCELED;
            case PICKED_UP -> next == IN_TRANSIT || next == CANCELED;
            case IN_TRANSIT -> next == OUT_FOR_DELIVERY || next == CANCELED;
            case OUT_FOR_DELIVERY -> next == DELIVERED;
            case DELIVERED, CANCELED -> false;
        };
    }

    public boolean isFinal() {
        return this == DELIVERED || this == CANCELED;
    }
}
