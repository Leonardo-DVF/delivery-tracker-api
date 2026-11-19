package io.github.leonardodvf.deliverytracker.tracking.controller;

import io.github.leonardodvf.deliverytracker.auth.config.SecurityConfig;
import io.github.leonardodvf.deliverytracker.shared.exception.ApiExceptionHandler;
import io.github.leonardodvf.deliverytracker.shipment.entity.Shipment;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.github.leonardodvf.deliverytracker.shipment.exception.InvalidStatusTransitionException;
import io.github.leonardodvf.deliverytracker.shipment.exception.ShipmentNotFoundException;
import io.github.leonardodvf.deliverytracker.tracking.dto.PublicTrackingResponse;
import io.github.leonardodvf.deliverytracker.tracking.dto.RecordTrackingEventRequest;
import io.github.leonardodvf.deliverytracker.tracking.dto.TrackingEventResponse;
import io.github.leonardodvf.deliverytracker.tracking.service.TrackingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TrackingController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class TrackingControllerTests {

    @Autowired private MockMvc mvc;
    @MockitoBean private TrackingService tracking;
    @MockitoBean private JwtDecoder decoder;
    private final UUID userId = UUID.randomUUID();
    private final UUID shipmentId = UUID.randomUUID();

    @Test
    void operatorRecordsEventForTokenSubject() throws Exception {
        var at = Instant.parse("2026-11-19T10:00:00Z");
        var request = new RecordTrackingEventRequest(ShipmentStatus.PICKED_UP, "Rio hub");
        when(tracking.record(userId, shipmentId, request)).thenReturn(new TrackingEventResponse(UUID.randomUUID(),
                ShipmentStatus.PICKED_UP, at, at, "OPERATOR", "Rio hub"));

        record("{\"status\":\"PICKED_UP\",\"location\":\"Rio hub\"}", as("OPERATOR"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.occurredAt").value("2026-11-19T10:00:00Z"))
                .andExpect(jsonPath("$.source").value("OPERATOR"))
                .andExpect(jsonPath("$.location").value("Rio hub"))
                .andExpect(jsonPath("$.shipment").doesNotExist());
        verify(tracking).record(userId, shipmentId, request);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"status\":null}",
        "{\"status\":\"POSTED\"}",
        "{\"status\":\"picked_up\"}",
        "{\"status\":\"PICKED_UP\",\"occurredAt\":\"2026-11-19T10:00:00Z\"}",
        "{\"status\":\"PICKED_UP\",\"source\":\"CARRIER\"}"
    })
    void rejectsMissingOrUnknownStatusAndServerControlledFields(String body) throws Exception {
        record(body, as("OPERATOR")).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(tracking);
    }

    @Test
    void rejectsLocationLongerThanTheColumn() throws Exception {
        record("{\"status\":\"PICKED_UP\",\"location\":\"" + "x".repeat(151) + "\"}", as("OPERATOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("location"));
        verifyNoInteractions(tracking);
    }

    @Test
    void reportsInvalidTransitionWithBothStatuses() throws Exception {
        when(tracking.record(any(), any(), any()))
                .thenThrow(new InvalidStatusTransitionException(ShipmentStatus.CREATED, ShipmentStatus.DELIVERED));

        record("{\"status\":\"DELIVERED\"}", as("OPERATOR"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Cannot change shipment status from CREATED to DELIVERED"))
                .andExpect(jsonPath("$.currentStatus").value("CREATED"))
                .andExpect(jsonPath("$.requestedStatus").value("DELIVERED"));
    }

    @Test
    void reportsConcurrentModificationAsConflict() throws Exception {
        when(tracking.record(any(), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Shipment.class, shipmentId));

        record("{\"status\":\"PICKED_UP\"}", as("OPERATOR"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Resource was modified by another request; reload it and try again"));
    }

    @Test
    void reportsUnknownShipmentAsNotFound() throws Exception {
        when(tracking.record(any(), any(), any())).thenThrow(new ShipmentNotFoundException());

        record("{\"status\":\"PICKED_UP\"}", as("OPERATOR")).andExpect(status().isNotFound());
    }

    @Test
    void onlyOperatorsCanRecordEvents() throws Exception {
        record("{\"status\":\"PICKED_UP\"}", null).andExpect(status().isUnauthorized());
        record("{\"status\":\"PICKED_UP\"}", as("CUSTOMER")).andExpect(status().isForbidden());
        record("{\"status\":\"PICKED_UP\"}", as("ADMIN")).andExpect(status().isForbidden());
        verifyNoInteractions(tracking);
    }

    @Test
    void rejectsMalformedShipmentId() throws Exception {
        mvc.perform(post("/api/v1/shipments/not-a-uuid/events").with(as("OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PICKED_UP\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tracking);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "OPERATOR"})
    void returnsHistoryForTokenSubject(String role) throws Exception {
        var at = Instant.parse("2026-11-19T10:00:00Z");
        when(tracking.history(userId, shipmentId)).thenReturn(List.of(
                new TrackingEventResponse(UUID.randomUUID(), ShipmentStatus.CREATED, at, at, "API", null),
                new TrackingEventResponse(UUID.randomUUID(), ShipmentStatus.PICKED_UP, at.plusSeconds(60),
                        at.plusSeconds(60), "OPERATOR", "Rio hub")));

        mvc.perform(get("/api/v1/shipments/" + shipmentId + "/events").with(as(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("CREATED"))
                .andExpect(jsonPath("$[1].location").value("Rio hub"));
    }

    @Test
    void historyOfMissingOrForeignShipmentIsNotFound() throws Exception {
        when(tracking.history(userId, shipmentId)).thenThrow(new ShipmentNotFoundException());

        mvc.perform(get("/api/v1/shipments/" + shipmentId + "/events").with(as("CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Shipment not found"));
    }

    @Test
    void historyRequiresAuthenticationAndAKnownRole() throws Exception {
        mvc.perform(get("/api/v1/shipments/" + shipmentId + "/events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/shipments/" + shipmentId + "/events").with(as("ADMIN"))).andExpect(status().isForbidden());
        verifyNoInteractions(tracking);
    }

    @Test
    void publicTrackingWorksWithoutAToken() throws Exception {
        var code = "3F2A9C1B7D4E4F0A8B6C5D2E1F0A9B8C";
        var at = Instant.parse("2026-11-19T10:00:00Z");
        when(tracking.publicTracking(code)).thenReturn(new PublicTrackingResponse(code, ShipmentStatus.PICKED_UP,
                "São Paulo", "SP", at, List.of(new PublicTrackingResponse.Event(ShipmentStatus.PICKED_UP, at, "Rio hub"))));

        mvc.perform(get("/api/v1/tracking/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingCode").value(code))
                .andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.destinationCity").value("São Paulo"))
                .andExpect(jsonPath("$.events[0].location").value("Rio hub"))
                .andExpect(jsonPath("$.events[0].source").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void publicTrackingAcceptsLowercaseCodes() throws Exception {
        var code = "3f2a9c1b7d4e4f0a8b6c5d2e1f0a9b8c";
        when(tracking.publicTracking(code)).thenThrow(new ShipmentNotFoundException());

        mvc.perform(get("/api/v1/tracking/" + code)).andExpect(status().isNotFound());
        verify(tracking).publicTracking(code);
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "3F2A9C1B7D4E4F0A8B6C5D2E1F0A9B8CA", "3F2A9C1B-7D4E-4F0A-8B6C-5D2E1F0A9B8C",
            "GGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG"})
    void publicTrackingRejectsMalformedCodesBeforeQuerying(String code) throws Exception {
        mvc.perform(get("/api/v1/tracking/" + code))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(tracking);
    }

    private RequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.subject(userId.toString())).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private ResultActions record(String body, RequestPostProcessor auth) throws Exception {
        var request = post("/api/v1/shipments/" + shipmentId + "/events").contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(auth == null ? request : request.with(auth));
    }
}
