package io.github.leonardodvf.deliverytracker.shipment.service;

import io.github.leonardodvf.deliverytracker.freight.exception.FreightRateUnavailableException;
import io.github.leonardodvf.deliverytracker.freight.service.FreightService;
import io.github.leonardodvf.deliverytracker.shipment.entity.Shipment;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.github.leonardodvf.deliverytracker.shipment.repository.ShipmentRepository;
import io.github.leonardodvf.deliverytracker.tracking.entity.TrackingEvent;
import io.github.leonardodvf.deliverytracker.tracking.repository.TrackingEventRepository;
import io.github.leonardodvf.deliverytracker.user.entity.*;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.request;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShipmentServiceTests {

    private final UserRepository users = mock(UserRepository.class);
    private final FreightService freight = mock(FreightService.class);
    private final ShipmentRepository shipments = mock(ShipmentRepository.class);
    private final TrackingEventRepository events = mock(TrackingEventRepository.class);
    private final ShipmentService service = new ShipmentService(users, freight, shipments, events);
    private final User customer = new User("Owner", "owner@example.test", "hash", UserRole.CUSTOMER);

    @BeforeEach
    void setup() {
        when(users.findById(customer.getId())).thenReturn(Optional.of(customer));
    }

    @Test
    void assignsAuthenticatedOwnerAndStoresBackendPriceWithInitialEvent() {
        when(freight.calculateAmount("01000000", new BigDecimal("2"))).thenReturn(new BigDecimal("15.00"));

        var result = service.create(customer.getId(), request());

        var saved = ArgumentCaptor.forClass(Shipment.class);
        verify(shipments).saveAndFlush(saved.capture());
        var event = ArgumentCaptor.forClass(TrackingEvent.class);
        verify(events).save(event.capture());
        assertThat(saved.getValue().getCustomer()).isSameAs(customer);
        assertThat(result.origin().contactName()).isEqualTo("Sender");
        assertThat(result.destination().contactName()).isEqualTo("Recipient");
        assertThat(result.freightAmount()).isEqualByComparingTo("15.00");
        assertThat(result.status()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(result.trackingCode()).matches("[0-9A-F]{32}");
        assertThat(result.updatedAt()).isEqualTo(result.createdAt());
        assertThat(event.getValue().getShipment()).isSameAs(saved.getValue());
        assertThat(event.getValue().getStatus()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(event.getValue().getOccurredAt()).isEqualTo(result.createdAt());
        assertThat(event.getValue().getReceivedAt()).isEqualTo(result.createdAt());
        assertThat(event.getValue().getSource()).isEqualTo("API");
        assertThat(event.getValue().getLocation()).isNull();
    }

    @Test
    void rejectsDeletedUserBeforeCalculatingOrWriting() {
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), request()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(freight, shipments, events);
    }

    @Test
    void rejectsOperatorEvenWhenServiceIsCalledDirectly() {
        var operator = new User("Operator", "operator@example.test", "hash", UserRole.OPERATOR);
        when(users.findById(operator.getId())).thenReturn(Optional.of(operator));

        assertThatThrownBy(() -> service.create(operator.getId(), request()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(freight, shipments, events);
    }

    @Test
    void doesNotSaveAnythingWhenTariffIsUnavailable() {
        when(freight.calculateAmount(anyString(), any())).thenThrow(new FreightRateUnavailableException());

        assertThatThrownBy(() -> service.create(customer.getId(), request()))
                .isInstanceOf(FreightRateUnavailableException.class);
        verifyNoInteractions(shipments, events);
    }

    @Test
    void doesNotCreateAnEventWhenShipmentPersistenceFails() {
        when(freight.calculateAmount(anyString(), any())).thenReturn(new BigDecimal("15.00"));
        when(shipments.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("conflict"));

        assertThatThrownBy(() -> service.create(customer.getId(), request()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        verifyNoInteractions(events);
    }

    @Test
    void generatesDifferentTrackingCodesForDifferentShipments() {
        when(freight.calculateAmount(anyString(), any())).thenReturn(new BigDecimal("15.00"));

        var first = service.create(customer.getId(), request());
        var second = service.create(customer.getId(), request());

        assertThat(first.trackingCode()).isNotEqualTo(second.trackingCode());
        assertThat(first.id()).isNotEqualTo(second.id());
    }
}
