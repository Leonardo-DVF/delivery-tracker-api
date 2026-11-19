package io.github.leonardodvf.deliverytracker.tracking.service;

import io.github.leonardodvf.deliverytracker.shipment.entity.Shipment;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.github.leonardodvf.deliverytracker.shipment.exception.InvalidStatusTransitionException;
import io.github.leonardodvf.deliverytracker.shipment.exception.ShipmentNotFoundException;
import io.github.leonardodvf.deliverytracker.shipment.repository.ShipmentRepository;
import io.github.leonardodvf.deliverytracker.tracking.dto.RecordTrackingEventRequest;
import io.github.leonardodvf.deliverytracker.tracking.dto.TrackingEventResponse;
import io.github.leonardodvf.deliverytracker.tracking.entity.TrackingEvent;
import io.github.leonardodvf.deliverytracker.tracking.repository.TrackingEventRepository;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.QUOTED_AT;
import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.shipment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TrackingServiceTests {

    private final Instant now = QUOTED_AT.plus(Duration.ofHours(1));
    private final UserRepository users = mock(UserRepository.class);
    private final ShipmentRepository shipments = mock(ShipmentRepository.class);
    private final TrackingEventRepository events = mock(TrackingEventRepository.class);
    private final TrackingService service = new TrackingService(users, shipments, events, Clock.fixed(now, ZoneOffset.UTC));
    private final User operator = new User("Operator", "operator@example.test", "hash", UserRole.OPERATOR);
    private final User customer = new User("Owner", "owner@example.test", "hash", UserRole.CUSTOMER);
    private Shipment shipment;

    @BeforeEach
    void setup() {
        shipment = shipment(customer, QUOTED_AT);
        when(users.findById(operator.getId())).thenReturn(Optional.of(operator));
        when(users.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(shipments.findById(shipment.getId())).thenReturn(Optional.of(shipment));
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void operatorMovesShipmentAndRecordsMatchingEvent() {
        var result = service.record(operator.getId(), shipment.getId(),
                new RecordTrackingEventRequest(ShipmentStatus.PICKED_UP, "  Rio hub  "));

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.PICKED_UP);
        assertThat(shipment.getUpdatedAt()).isEqualTo(now);
        var saved = ArgumentCaptor.forClass(TrackingEvent.class);
        var order = inOrder(shipments, events);
        order.verify(shipments).saveAndFlush(shipment);
        order.verify(events).save(saved.capture());
        assertThat(saved.getValue().getShipment()).isSameAs(shipment);
        assertThat(result.status()).isEqualTo(ShipmentStatus.PICKED_UP);
        assertThat(result.occurredAt()).isEqualTo(now);
        assertThat(result.receivedAt()).isEqualTo(now);
        assertThat(result.source()).isEqualTo("OPERATOR");
        assertThat(result.location()).isEqualTo("Rio hub");
    }

    @Test
    void blankLocationIsStoredAsNull() {
        var result = service.record(operator.getId(), shipment.getId(),
                new RecordTrackingEventRequest(ShipmentStatus.PICKED_UP, "   "));

        assertThat(result.location()).isNull();
    }

    @Test
    void rejectsInvalidTransitionWithoutWriting() {
        assertThatThrownBy(() -> service.record(operator.getId(), shipment.getId(),
                new RecordTrackingEventRequest(ShipmentStatus.DELIVERED, null)))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CREATED);
        verify(shipments, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void doesNotRecordEventWhenAConcurrentChangeWins() {
        when(shipments.saveAndFlush(shipment)).thenThrow(new ObjectOptimisticLockingFailureException(Shipment.class, shipment.getId()));

        assertThatThrownBy(() -> service.record(operator.getId(), shipment.getId(),
                new RecordTrackingEventRequest(ShipmentStatus.PICKED_UP, null)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verifyNoInteractions(events);
    }

    @Test
    void reportsUnknownShipmentAsNotFound() {
        assertThatThrownBy(() -> service.record(operator.getId(), UUID.randomUUID(),
                new RecordTrackingEventRequest(ShipmentStatus.PICKED_UP, null)))
                .isInstanceOf(ShipmentNotFoundException.class);
        verifyNoInteractions(events);
    }

    @Test
    void rejectsCustomersEvenWhenServiceIsCalledDirectly() {
        assertThatThrownBy(() -> service.record(customer.getId(), shipment.getId(),
                new RecordTrackingEventRequest(ShipmentStatus.PICKED_UP, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(shipments, events);
    }

    @Test
    void rejectsDeletedUsers() {
        assertThatThrownBy(() -> service.record(UUID.randomUUID(), shipment.getId(),
                new RecordTrackingEventRequest(ShipmentStatus.PICKED_UP, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(shipments, events);
    }

    @Test
    void customerReadsHistoryOfOwnShipmentOnly() {
        var event = TrackingEvent.initial(shipment);
        when(shipments.existsByIdAndCustomerId(shipment.getId(), customer.getId())).thenReturn(true);
        when(events.findByShipmentIdOrderByOccurredAtAscReceivedAtAsc(shipment.getId())).thenReturn(List.of(event));

        assertThat(service.history(customer.getId(), shipment.getId()))
                .extracting(TrackingEventResponse::id).containsExactly(event.getId());
        verify(shipments, never()).existsById(any());
    }

    @Test
    void customerGetsNotFoundForAnotherCustomersHistory() {
        var foreign = UUID.randomUUID();
        when(shipments.existsByIdAndCustomerId(foreign, customer.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.history(customer.getId(), foreign)).isInstanceOf(ShipmentNotFoundException.class);
        verifyNoInteractions(events);
    }

    @Test
    void operatorReadsAnyHistoryButUnknownShipmentIsNotFound() {
        when(shipments.existsById(shipment.getId())).thenReturn(true);
        when(events.findByShipmentIdOrderByOccurredAtAscReceivedAtAsc(shipment.getId())).thenReturn(List.of());

        assertThat(service.history(operator.getId(), shipment.getId())).isEmpty();
        assertThatThrownBy(() -> service.history(operator.getId(), UUID.randomUUID()))
                .isInstanceOf(ShipmentNotFoundException.class);
        verify(shipments, never()).existsByIdAndCustomerId(any(), any());
    }

    @Test
    void historyRejectsDeletedUsers() {
        assertThatThrownBy(() -> service.history(UUID.randomUUID(), shipment.getId()))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(shipments, events);
    }
}
