package io.github.leonardodvf.deliverytracker.webhook.service;

import io.github.leonardodvf.deliverytracker.shipment.entity.Shipment;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.github.leonardodvf.deliverytracker.shipment.exception.InvalidStatusTransitionException;
import io.github.leonardodvf.deliverytracker.shipment.exception.ShipmentNotFoundException;
import io.github.leonardodvf.deliverytracker.shipment.repository.ShipmentRepository;
import io.github.leonardodvf.deliverytracker.tracking.entity.TrackingEvent;
import io.github.leonardodvf.deliverytracker.tracking.repository.TrackingEventRepository;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.webhook.config.CarrierWebhookProperties;
import io.github.leonardodvf.deliverytracker.webhook.dto.WebhookReceipt.Outcome;
import io.github.leonardodvf.deliverytracker.webhook.exception.InvalidWebhookPayloadException;
import io.github.leonardodvf.deliverytracker.webhook.exception.InvalidWebhookSignatureException;
import io.github.leonardodvf.deliverytracker.webhook.security.WebhookSignatureVerifier;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.QUOTED_AT;
import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.shipment;
import static io.github.leonardodvf.deliverytracker.webhook.CarrierWebhookSigner.TEST_SECRET;
import static io.github.leonardodvf.deliverytracker.webhook.CarrierWebhookSigner.signature;
import static io.github.leonardodvf.deliverytracker.webhook.CarrierWebhookSigner.timestamp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CarrierWebhookServiceTests {

    private final Instant now = QUOTED_AT.plus(Duration.ofHours(2));
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final CarrierWebhookProperties properties = new CarrierWebhookProperties(TEST_SECRET, Duration.ofMinutes(5));
    private final ShipmentRepository shipments = mock(ShipmentRepository.class);
    private final TrackingEventRepository events = mock(TrackingEventRepository.class);
    private final CarrierWebhookService service = new CarrierWebhookService(new WebhookSignatureVerifier(properties, clock),
            JsonMapper.builder().build(), Validation.buildDefaultValidatorFactory().getValidator(),
            new TransactionTemplate(mock(PlatformTransactionManager.class)), shipments, events, properties, clock);
    private final User customer = new User("Owner", "owner@example.test", "hash", UserRole.CUSTOMER);
    private Shipment shipment;

    @BeforeEach
    void setup() {
        shipment = shipment(customer, QUOTED_AT);
        when(shipments.findByTrackingCode(shipment.getTrackingCode())).thenReturn(Optional.of(shipment));
    }

    @Test
    void appliesTheNextStatusKeepingCarrierAndServerTimesApart() {
        var occurredAt = now.minus(Duration.ofMinutes(40));

        var receipt = send(event("evt-1", "PICKED_UP", occurredAt, "  Rio hub  "));

        assertThat(receipt.outcome()).isEqualTo(Outcome.APPLIED);
        assertThat(receipt.eventId()).isEqualTo("evt-1");
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.PICKED_UP);
        assertThat(shipment.getUpdatedAt()).isEqualTo(now);
        var saved = ArgumentCaptor.forClass(TrackingEvent.class);
        var order = inOrder(shipments, events);
        order.verify(shipments).saveAndFlush(shipment);
        order.verify(events).saveAndFlush(saved.capture());
        var event = saved.getValue();
        assertThat(event.getStatus()).isEqualTo(ShipmentStatus.PICKED_UP);
        assertThat(event.getCarrierEventId()).isEqualTo("evt-1");
        assertThat(event.getSource()).isEqualTo("CARRIER");
        assertThat(event.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(event.getReceivedAt()).isEqualTo(now);
        assertThat(event.getLocation()).isEqualTo("Rio hub");
    }

    @Test
    void acceptsLowercaseTrackingCodesAndIgnoresUnknownFields() {
        var body = event("evt-1", "PICKED_UP", now, null)
                .replace(shipment.getTrackingCode(), shipment.getTrackingCode().toLowerCase(Locale.ROOT))
                .replace("{", "{\"carrierInternalRoute\":\"R-17\",");

        assertThat(send(body).outcome()).isEqualTo(Outcome.APPLIED);
    }

    @Test
    void reportsAKnownEventIdAsDuplicateWithoutTouchingTheShipment() {
        when(events.existsByCarrierEventId("evt-1")).thenReturn(true);

        assertThat(send(event("evt-1", "PICKED_UP", now, null)).outcome()).isEqualTo(Outcome.DUPLICATE);
        verifyNoInteractions(shipments);
        verify(events, never()).saveAndFlush(any());
    }

    @Test
    void reportsDuplicateWhenAConcurrentDeliveryWinsTheUniqueConstraint() {
        when(events.existsByCarrierEventId("evt-1")).thenReturn(false, true);
        when(events.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_tracking_events_carrier_event"));

        assertThat(send(event("evt-1", "PICKED_UP", now, null)).outcome()).isEqualTo(Outcome.DUPLICATE);
    }

    @Test
    void rethrowsIntegrityViolationsThatAreNotDuplicates() {
        when(events.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("something else"));

        assertThatThrownBy(() -> send(event("evt-1", "PICKED_UP", now, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest(name = "{0} then {1}")
    @CsvSource({
        "IN_TRANSIT,PICKED_UP",          // already passed
        "IN_TRANSIT,IN_TRANSIT",         // same status under a new id
        "PICKED_UP,CREATED",
        "OUT_FOR_DELIVERY,CANCELED",     // never allowed from here
        "DELIVERED,CANCELED",            // final
        "CANCELED,PICKED_UP"             // final
    })
    void ignoresEventsThatCanNeverApply(ShipmentStatus current, ShipmentStatus incoming) {
        moveTo(current);

        assertThat(send(event("evt-9", incoming.name(), now, null)).outcome()).isEqualTo(Outcome.IGNORED);
        assertThat(shipment.getStatus()).isEqualTo(current);
        verify(shipments, never()).saveAndFlush(any());
        verify(events, never()).saveAndFlush(any());
    }

    @ParameterizedTest(name = "{0} then {1}")
    @CsvSource({"CREATED,IN_TRANSIT", "CREATED,DELIVERED", "PICKED_UP,OUT_FOR_DELIVERY"})
    void asksForARetryWhenAnEarlierEventHasNotArrivedYet(ShipmentStatus current, ShipmentStatus incoming) {
        moveTo(current);

        assertThatThrownBy(() -> send(event("evt-9", incoming.name(), now, null)))
                .isInstanceOf(InvalidStatusTransitionException.class);
        verify(events, never()).saveAndFlush(any());
    }

    @Test
    void reportsUnknownTrackingCodeAsNotFound() {
        var body = event("evt-1", "PICKED_UP", now, null).replace(shipment.getTrackingCode(), "A".repeat(32));

        assertThatThrownBy(() -> send(body)).isInstanceOf(ShipmentNotFoundException.class);
    }

    @Test
    void checksTheSignatureBeforeParsingOrQuerying() {
        var body = "not even json";

        assertThatThrownBy(() -> service.receive(timestamp(now), "sha256=" + "0".repeat(64), bytes(body)))
                .isInstanceOf(InvalidWebhookSignatureException.class);
        verifyNoInteractions(shipments, events);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "null", "[]", "{\"status\":\"RETURNED\"}", "{\"occurredAt\":\"yesterday\"}"})
    void rejectsMalformedPayloads(String body) {
        assertThatThrownBy(() -> send(body))
                .isInstanceOf(InvalidWebhookPayloadException.class)
                .extracting(ex -> ((InvalidWebhookPayloadException) ex).getBody().getDetail())
                .isEqualTo("Malformed webhook payload");
        verifyNoInteractions(shipments, events);
    }

    @Test
    void reportsEveryInvalidFieldSortedByName() {
        var body = "{\"eventId\":\"bad id\",\"trackingCode\":\"XYZ\",\"location\":\"" + "x".repeat(151) + "\"}";

        assertThatThrownBy(() -> send(body))
                .isInstanceOf(InvalidWebhookPayloadException.class)
                .satisfies(ex -> {
                    var problem = ((InvalidWebhookPayloadException) ex).getBody();
                    assertThat(problem.getDetail()).isEqualTo("Webhook payload validation failed");
                    @SuppressWarnings("unchecked")
                    var errors = (List<InvalidWebhookPayloadException.FieldError>) problem.getProperties().get("errors");
                    assertThat(errors).extracting(InvalidWebhookPayloadException.FieldError::field)
                            .containsExactly("eventId", "location", "occurredAt", "status", "trackingCode");
                });
        verifyNoInteractions(shipments, events);
    }

    @Test
    void rejectsOccurredAtBeyondTheAllowedClockSkew() {
        assertThat(send(event("evt-1", "PICKED_UP", now.plus(Duration.ofMinutes(5)), null)).outcome())
                .isEqualTo(Outcome.APPLIED);
        assertThatThrownBy(() -> send(event("evt-2", "IN_TRANSIT", now.plus(Duration.ofMinutes(5)).plusSeconds(1), null)))
                .isInstanceOf(InvalidWebhookPayloadException.class);
    }

    @Test
    void skipsAheadOnlyForProgressStatusesBeyondTheNextOne() {
        assertThat(CarrierWebhookService.skipsAhead(ShipmentStatus.CREATED, ShipmentStatus.PICKED_UP)).isFalse();
        assertThat(CarrierWebhookService.skipsAhead(ShipmentStatus.CREATED, ShipmentStatus.IN_TRANSIT)).isTrue();
        assertThat(CarrierWebhookService.skipsAhead(ShipmentStatus.IN_TRANSIT, ShipmentStatus.CANCELED)).isFalse();
        assertThat(CarrierWebhookService.skipsAhead(ShipmentStatus.CANCELED, ShipmentStatus.DELIVERED)).isFalse();
    }

    private void moveTo(ShipmentStatus target) {
        var path = switch (target) {
            case CREATED -> List.<ShipmentStatus>of();
            case PICKED_UP -> List.of(ShipmentStatus.PICKED_UP);
            case IN_TRANSIT -> List.of(ShipmentStatus.PICKED_UP, ShipmentStatus.IN_TRANSIT);
            case OUT_FOR_DELIVERY -> List.of(ShipmentStatus.PICKED_UP, ShipmentStatus.IN_TRANSIT, ShipmentStatus.OUT_FOR_DELIVERY);
            case DELIVERED -> List.of(ShipmentStatus.PICKED_UP, ShipmentStatus.IN_TRANSIT, ShipmentStatus.OUT_FOR_DELIVERY,
                    ShipmentStatus.DELIVERED);
            case CANCELED -> List.of(ShipmentStatus.CANCELED);
        };
        path.forEach(status -> shipment.transitionTo(status, QUOTED_AT.plusSeconds(1)));
    }

    private String event(String eventId, String status, Instant occurredAt, String location) {
        var fields = new java.util.LinkedHashMap<String, Object>(Map.of(
                "eventId", eventId, "trackingCode", shipment.getTrackingCode(), "status", status,
                "occurredAt", occurredAt.toString()));
        if (location != null) {
            fields.put("location", location);
        }
        return JsonMapper.builder().build().writeValueAsString(fields);
    }

    private io.github.leonardodvf.deliverytracker.webhook.dto.WebhookReceipt send(String body) {
        var timestamp = timestamp(now);
        return service.receive(timestamp, signature(timestamp, body), bytes(body));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
