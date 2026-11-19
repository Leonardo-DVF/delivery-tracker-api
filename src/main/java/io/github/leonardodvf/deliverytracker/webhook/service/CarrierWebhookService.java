package io.github.leonardodvf.deliverytracker.webhook.service;

import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.github.leonardodvf.deliverytracker.shipment.exception.InvalidStatusTransitionException;
import io.github.leonardodvf.deliverytracker.shipment.exception.ShipmentNotFoundException;
import io.github.leonardodvf.deliverytracker.shipment.repository.ShipmentRepository;
import io.github.leonardodvf.deliverytracker.tracking.entity.TrackingEvent;
import io.github.leonardodvf.deliverytracker.tracking.repository.TrackingEventRepository;
import io.github.leonardodvf.deliverytracker.webhook.config.CarrierWebhookProperties;
import io.github.leonardodvf.deliverytracker.webhook.dto.CarrierEventPayload;
import io.github.leonardodvf.deliverytracker.webhook.dto.WebhookReceipt;
import io.github.leonardodvf.deliverytracker.webhook.dto.WebhookReceipt.Outcome;
import io.github.leonardodvf.deliverytracker.webhook.exception.InvalidWebhookPayloadException;
import io.github.leonardodvf.deliverytracker.webhook.exception.InvalidWebhookPayloadException.FieldError;
import io.github.leonardodvf.deliverytracker.webhook.exception.InvalidWebhookSignatureException;
import io.github.leonardodvf.deliverytracker.webhook.security.WebhookSignatureVerifier;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus.*;

/**
 * Carriers deliver webhooks at least once and not always in order. The rules:
 * <ul>
 *   <li>Signature is checked on the raw bytes before anything is parsed.</li>
 *   <li>An event id seen before is a DUPLICATE, guaranteed by the unique constraint even under concurrency.</li>
 *   <li>A status that skips ahead (its predecessor has not arrived yet) gets 409 so the carrier retries later.</li>
 *   <li>A status that can never apply (already passed, or the shipment is final) is IGNORED with 200,
 *       so the carrier stops retrying something that will never succeed.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarrierWebhookService {

    private static final List<ShipmentStatus> PROGRESS = List.of(CREATED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED);

    private final WebhookSignatureVerifier verifier;
    private final ObjectMapper json;
    private final Validator validator;
    private final TransactionTemplate transactions;
    private final ShipmentRepository shipments;
    private final TrackingEventRepository events;
    private final CarrierWebhookProperties properties;
    private final Clock clock;

    public WebhookReceipt receive(String timestamp, String signature, byte[] body) {
        try {
            verifier.verify(timestamp, signature, body);
        } catch (InvalidWebhookSignatureException exception) {
            log.warn("Rejected carrier webhook: {}", exception.getReason());
            throw exception;
        }
        var event = parse(body);
        try {
            return transactions.execute(status -> apply(event));
        } catch (DataIntegrityViolationException exception) {
            // A concurrent delivery of the same event committed first and the unique constraint stopped this one.
            if (events.existsByCarrierEventId(event.eventId())) {
                return new WebhookReceipt(event.eventId(), Outcome.DUPLICATE);
            }
            throw exception;
        }
    }

    private WebhookReceipt apply(CarrierEventPayload event) {
        if (events.existsByCarrierEventId(event.eventId())) {
            return new WebhookReceipt(event.eventId(), Outcome.DUPLICATE);
        }
        var shipment = shipments.findByTrackingCode(event.trackingCode().toUpperCase(Locale.ROOT))
                .orElseThrow(ShipmentNotFoundException::new);
        var current = shipment.getStatus();
        if (!current.canTransitionTo(event.status())) {
            if (skipsAhead(current, event.status())) {
                throw new InvalidStatusTransitionException(current, event.status());
            }
            log.info("Ignored carrier event {}: {} cannot follow {}", event.eventId(), event.status(), current);
            return new WebhookReceipt(event.eventId(), Outcome.IGNORED);
        }
        shipment.transitionTo(event.status(), clock.instant());
        shipments.saveAndFlush(shipment);
        events.saveAndFlush(TrackingEvent.fromCarrier(shipment, event.eventId(), event.occurredAt(), event.location()));
        return new WebhookReceipt(event.eventId(), Outcome.APPLIED);
    }

    /** A later progress status whose predecessor may still arrive; it can become valid, so it is worth retrying. */
    static boolean skipsAhead(ShipmentStatus current, ShipmentStatus next) {
        return !current.isFinal() && PROGRESS.indexOf(next) > PROGRESS.indexOf(current) + 1;
    }

    private CarrierEventPayload parse(byte[] body) {
        CarrierEventPayload event;
        try {
            event = json.readValue(body, CarrierEventPayload.class);
        } catch (JacksonException exception) {
            throw InvalidWebhookPayloadException.malformed();
        }
        if (event == null) {
            throw InvalidWebhookPayloadException.malformed();
        }
        var errors = new ArrayList<FieldError>();
        validator.validate(event).forEach(violation ->
                errors.add(new FieldError(violation.getPropertyPath().toString(), violation.getMessage())));
        if (event.occurredAt() != null && event.occurredAt().isAfter(clock.instant().plus(properties.tolerance()))) {
            errors.add(new FieldError("occurredAt", "must not be in the future"));
        }
        if (!errors.isEmpty()) {
            errors.sort(Comparator.comparing(FieldError::field));
            throw InvalidWebhookPayloadException.invalid(errors);
        }
        return event;
    }
}
