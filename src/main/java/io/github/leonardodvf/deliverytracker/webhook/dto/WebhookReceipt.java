package io.github.leonardodvf.deliverytracker.webhook.dto;

/** Any 2xx tells the carrier to stop retrying; the outcome says what happened to the event. */
public record WebhookReceipt(String eventId, Outcome outcome) {

    public enum Outcome {
        /** Status changed and the event was added to the history. */
        APPLIED,
        /** This event id was already processed; nothing changed. */
        DUPLICATE,
        /** Stale or impossible for the current status (e.g. already passed, or the shipment is final); not stored. */
        IGNORED
    }
}
