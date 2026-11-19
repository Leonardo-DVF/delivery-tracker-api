package io.github.leonardodvf.deliverytracker.shipment.service;

import io.github.leonardodvf.deliverytracker.freight.entity.FreightQuote;
import io.github.leonardodvf.deliverytracker.freight.repository.FreightQuoteRepository;
import io.github.leonardodvf.deliverytracker.shipment.dto.ShipmentResponse;
import io.github.leonardodvf.deliverytracker.shipment.entity.Shipment;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.github.leonardodvf.deliverytracker.shipment.exception.CancellationNotAllowedException;
import io.github.leonardodvf.deliverytracker.shipment.exception.DestinationMismatchException;
import io.github.leonardodvf.deliverytracker.shipment.exception.InvalidStatusTransitionException;
import io.github.leonardodvf.deliverytracker.shipment.exception.FreightQuoteAlreadyUsedException;
import io.github.leonardodvf.deliverytracker.shipment.exception.FreightQuoteExpiredException;
import io.github.leonardodvf.deliverytracker.shipment.exception.FreightQuoteNotFoundException;
import io.github.leonardodvf.deliverytracker.shipment.exception.ShipmentNotFoundException;
import io.github.leonardodvf.deliverytracker.shipment.repository.ShipmentRepository;
import io.github.leonardodvf.deliverytracker.tracking.entity.TrackingEvent;
import io.github.leonardodvf.deliverytracker.tracking.repository.TrackingEventRepository;
import io.github.leonardodvf.deliverytracker.user.entity.*;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShipmentServiceTests {

    private final Instant now = QUOTED_AT.plus(Duration.ofMinutes(10));
    private final UserRepository users = mock(UserRepository.class);
    private final FreightQuoteRepository quotes = mock(FreightQuoteRepository.class);
    private final ShipmentRepository shipments = mock(ShipmentRepository.class);
    private final TrackingEventRepository events = mock(TrackingEventRepository.class);
    private final ShipmentService service = new ShipmentService(users, quotes, shipments, events,
            Clock.fixed(now, ZoneOffset.UTC));
    private final User customer = new User("Owner", "owner@example.test", "hash", UserRole.CUSTOMER);
    private final FreightQuote quote = quote(customer);

    @BeforeEach
    void setup() {
        when(users.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(quotes.findById(quote.getId())).thenReturn(Optional.of(quote));
    }

    @Test
    void copiesWeightAndPriceFromQuoteAndStoresInitialEvent() {
        var result = service.create(customer.getId(), request(quote.getId()));

        var saved = ArgumentCaptor.forClass(Shipment.class);
        verify(shipments).saveAndFlush(saved.capture());
        var event = ArgumentCaptor.forClass(TrackingEvent.class);
        verify(events).save(event.capture());
        assertThat(saved.getValue().getCustomer()).isSameAs(customer);
        assertThat(saved.getValue().getQuote()).isSameAs(quote);
        assertThat(result.quoteId()).isEqualTo(quote.getId());
        assertThat(result.weightKg()).isEqualByComparingTo("2.000");
        assertThat(result.freightAmount()).isEqualByComparingTo("15.00");
        assertThat(result.lengthCm()).isEqualByComparingTo("30");
        assertThat(result.origin().contactName()).isEqualTo("Sender");
        assertThat(result.destination().contactName()).isEqualTo("Recipient");
        assertThat(result.status()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(result.trackingCode()).matches("[0-9A-F]{32}");
        assertThat(result.createdAt()).isEqualTo(now);
        assertThat(result.updatedAt()).isEqualTo(now);
        assertThat(event.getValue().getShipment()).isSameAs(saved.getValue());
        assertThat(event.getValue().getStatus()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(event.getValue().getOccurredAt()).isEqualTo(now);
        assertThat(event.getValue().getReceivedAt()).isEqualTo(now);
        assertThat(event.getValue().getSource()).isEqualTo("API");
        assertThat(event.getValue().getLocation()).isNull();
    }

    @Test
    void rejectsDeletedUserBeforeReadingQuoteOrWriting() {
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), request(quote.getId())))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(quotes, shipments, events);
    }

    @Test
    void rejectsOperatorEvenWhenServiceIsCalledDirectly() {
        var operator = new User("Operator", "operator@example.test", "hash", UserRole.OPERATOR);
        when(users.findById(operator.getId())).thenReturn(Optional.of(operator));

        assertThatThrownBy(() -> service.create(operator.getId(), request(quote.getId())))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(quotes, shipments, events);
    }

    @Test
    void reportsUnknownQuoteAsNotFound() {
        assertThatThrownBy(() -> service.create(customer.getId(), request(UUID.randomUUID())))
                .isInstanceOf(FreightQuoteNotFoundException.class);
        verifyNoInteractions(shipments, events);
    }

    @Test
    void reportsQuoteOfAnotherUserAsNotFoundToAvoidRevealingIt() {
        var other = new User("Other", "other@example.test", "hash", UserRole.CUSTOMER);
        var foreign = quote(other);
        when(quotes.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.create(customer.getId(), request(foreign.getId())))
                .isInstanceOf(FreightQuoteNotFoundException.class);
        verifyNoInteractions(shipments, events);
    }

    @Test
    void rejectsQuoteAtItsExactExpirationInstant() {
        var expiring = quote(customer, "01000000", now.minus(Duration.ofMinutes(30)));
        when(quotes.findById(expiring.getId())).thenReturn(Optional.of(expiring));

        assertThatThrownBy(() -> service.create(customer.getId(), request(expiring.getId())))
                .isInstanceOf(FreightQuoteExpiredException.class)
                .extracting(ex -> ((FreightQuoteExpiredException) ex).getStatusCode().value()).isEqualTo(422);
        verifyNoInteractions(shipments, events);
    }

    @Test
    void acceptsQuoteOneNanosecondBeforeExpiration() {
        var almostExpired = quote(customer, "01000000", now.minus(Duration.ofMinutes(30)).plusNanos(1));
        when(quotes.findById(almostExpired.getId())).thenReturn(Optional.of(almostExpired));

        assertThat(service.create(customer.getId(), request(almostExpired.getId())).quoteId())
                .isEqualTo(almostExpired.getId());
    }

    @Test
    void rejectsDestinationDifferentFromTheQuotedPostalCode() {
        var elsewhere = quote(customer, "60000000", QUOTED_AT);
        when(quotes.findById(elsewhere.getId())).thenReturn(Optional.of(elsewhere));

        assertThatThrownBy(() -> service.create(customer.getId(), request(elsewhere.getId())))
                .isInstanceOf(DestinationMismatchException.class)
                .extracting(ex -> ((DestinationMismatchException) ex).getStatusCode().value()).isEqualTo(422);
        verifyNoInteractions(shipments, events);
    }

    @Test
    void rejectsQuoteThatAlreadyBackedAShipment() {
        when(shipments.existsByQuoteId(quote.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.create(customer.getId(), request(quote.getId())))
                .isInstanceOf(FreightQuoteAlreadyUsedException.class)
                .extracting(ex -> ((FreightQuoteAlreadyUsedException) ex).getStatusCode().value()).isEqualTo(409);
        verify(shipments, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void doesNotCreateAnEventWhenShipmentPersistenceFails() {
        when(shipments.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("conflict"));

        assertThatThrownBy(() -> service.create(customer.getId(), request(quote.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
        verifyNoInteractions(events);
    }

    @Test
    void customerListIsAlwaysScopedToTheirOwnShipmentsNewestFirst() {
        var expected = PageRequest.of(2, 5, ShipmentService.NEWEST_FIRST);
        var stored = shipment();
        when(shipments.findByCustomerId(customer.getId(), expected))
                .thenReturn(new PageImpl<>(List.of(stored), expected, 11));

        var page = service.list(customer.getId(), null, 2, 5);

        assertThat(page.content()).extracting(ShipmentResponse::id).containsExactly(stored.getId());
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(5);
        assertThat(page.totalElements()).isEqualTo(11);
        assertThat(page.totalPages()).isEqualTo(3);
        verify(shipments, never()).findAll(any(Pageable.class));
        verify(shipments, never()).findByStatus(any(), any());
    }

    @Test
    void customerStatusFilterStaysScopedToTheCustomer() {
        var expected = PageRequest.of(0, 20, ShipmentService.NEWEST_FIRST);
        when(shipments.findByCustomerIdAndStatus(customer.getId(), ShipmentStatus.CREATED, expected))
                .thenReturn(Page.empty(expected));

        service.list(customer.getId(), ShipmentStatus.CREATED, 0, 20);

        verify(shipments).findByCustomerIdAndStatus(customer.getId(), ShipmentStatus.CREATED, expected);
        verify(shipments, never()).findByStatus(any(), any());
    }

    @Test
    void operatorListsAllShipmentsWithOrWithoutStatusFilter() {
        var operator = new User("Operator", "operator@example.test", "hash", UserRole.OPERATOR);
        when(users.findById(operator.getId())).thenReturn(Optional.of(operator));
        var expected = PageRequest.of(0, 20, ShipmentService.NEWEST_FIRST);
        when(shipments.findAll(expected)).thenReturn(Page.empty(expected));
        when(shipments.findByStatus(ShipmentStatus.CREATED, expected)).thenReturn(Page.empty(expected));

        service.list(operator.getId(), null, 0, 20);
        service.list(operator.getId(), ShipmentStatus.CREATED, 0, 20);

        verify(shipments).findAll(expected);
        verify(shipments).findByStatus(ShipmentStatus.CREATED, expected);
        verify(shipments, never()).findByCustomerId(any(), any());
    }

    @Test
    void customerCannotReadAnotherCustomersShipment() {
        var id = UUID.randomUUID();
        when(shipments.findByIdAndCustomerId(id, customer.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(customer.getId(), id)).isInstanceOf(ShipmentNotFoundException.class)
                .extracting(ex -> ((ShipmentNotFoundException) ex).getStatusCode().value()).isEqualTo(404);
        verify(shipments, never()).findById(any());
    }

    @Test
    void customerReadsOwnShipmentAndOperatorReadsAny() {
        var stored = shipment();
        when(shipments.findByIdAndCustomerId(stored.getId(), customer.getId())).thenReturn(Optional.of(stored));
        var operator = new User("Operator", "operator@example.test", "hash", UserRole.OPERATOR);
        when(users.findById(operator.getId())).thenReturn(Optional.of(operator));
        when(shipments.findById(stored.getId())).thenReturn(Optional.of(stored));

        assertThat(service.get(customer.getId(), stored.getId()).id()).isEqualTo(stored.getId());
        assertThat(service.get(operator.getId(), stored.getId()).id()).isEqualTo(stored.getId());
    }

    @Test
    void queriesRejectDeletedUsers() {
        var unknown = UUID.randomUUID();
        assertThatThrownBy(() -> service.list(unknown, null, 0, 20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.get(unknown, UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(shipments);
    }

    @Test
    void customerCancelsOwnShipmentBeforePickupAndRecordsEvent() {
        var stored = shipment();
        when(shipments.findByIdAndCustomerId(stored.getId(), customer.getId())).thenReturn(Optional.of(stored));

        var result = service.cancel(customer.getId(), stored.getId());

        assertThat(result.status()).isEqualTo(ShipmentStatus.CANCELED);
        assertThat(result.updatedAt()).isEqualTo(now);
        var event = ArgumentCaptor.forClass(TrackingEvent.class);
        var order = inOrder(shipments, events);
        order.verify(shipments).saveAndFlush(stored);
        order.verify(events).save(event.capture());
        assertThat(event.getValue().getStatus()).isEqualTo(ShipmentStatus.CANCELED);
        assertThat(event.getValue().getSource()).isEqualTo("CUSTOMER");
        verify(shipments, never()).findById(any());
    }

    @Test
    void customerCannotCancelAfterPickup() {
        var stored = shipment();
        stored.transitionTo(ShipmentStatus.PICKED_UP, now);
        when(shipments.findByIdAndCustomerId(stored.getId(), customer.getId())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.cancel(customer.getId(), stored.getId()))
                .isInstanceOf(CancellationNotAllowedException.class)
                .extracting(ex -> ((CancellationNotAllowedException) ex).getStatusCode().value()).isEqualTo(409);
        assertThat(stored.getStatus()).isEqualTo(ShipmentStatus.PICKED_UP);
        verify(shipments, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void customerCannotCancelAnotherCustomersShipment() {
        var id = UUID.randomUUID();
        when(shipments.findByIdAndCustomerId(id, customer.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(customer.getId(), id)).isInstanceOf(ShipmentNotFoundException.class);
        verify(shipments, never()).findById(any());
        verifyNoInteractions(events);
    }

    @Test
    void operatorCancelsInTransitShipmentButNotOneOutForDelivery() {
        var operator = new User("Operator", "operator@example.test", "hash", UserRole.OPERATOR);
        when(users.findById(operator.getId())).thenReturn(Optional.of(operator));
        var inTransit = shipment();
        inTransit.transitionTo(ShipmentStatus.PICKED_UP, now);
        inTransit.transitionTo(ShipmentStatus.IN_TRANSIT, now);
        var outForDelivery = shipment();
        outForDelivery.transitionTo(ShipmentStatus.PICKED_UP, now);
        outForDelivery.transitionTo(ShipmentStatus.IN_TRANSIT, now);
        outForDelivery.transitionTo(ShipmentStatus.OUT_FOR_DELIVERY, now);
        when(shipments.findById(inTransit.getId())).thenReturn(Optional.of(inTransit));
        when(shipments.findById(outForDelivery.getId())).thenReturn(Optional.of(outForDelivery));

        assertThat(service.cancel(operator.getId(), inTransit.getId()).status()).isEqualTo(ShipmentStatus.CANCELED);
        var event = ArgumentCaptor.forClass(TrackingEvent.class);
        verify(events).save(event.capture());
        assertThat(event.getValue().getSource()).isEqualTo("OPERATOR");
        assertThatThrownBy(() -> service.cancel(operator.getId(), outForDelivery.getId()))
                .isInstanceOf(InvalidStatusTransitionException.class);
        verify(shipments, never()).findByIdAndCustomerId(any(), any());
    }

    private Shipment shipment() {
        var input = request(quote.getId());
        return new Shipment(customer, quote, input.origin().toAddress(), input.destination().toAddress(),
                input.lengthCm(), input.widthCm(), input.heightCm(), now);
    }
}
