package io.github.leonardodvf.deliverytracker.shipment.controller;

import io.github.leonardodvf.deliverytracker.auth.config.SecurityConfig;
import io.github.leonardodvf.deliverytracker.shipment.dto.*;
import io.github.leonardodvf.deliverytracker.shipment.entity.Shipment;
import io.github.leonardodvf.deliverytracker.shipment.exception.*;
import io.github.leonardodvf.deliverytracker.shipment.service.ShipmentService;
import io.github.leonardodvf.deliverytracker.shared.exception.ApiExceptionHandler;
import io.github.leonardodvf.deliverytracker.user.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.ErrorResponseException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.QUOTED_AT;
import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.quote;
import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.request;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShipmentController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class ShipmentControllerTests {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockitoBean private ShipmentService shipments;
    @MockitoBean private JwtDecoder decoder;
    private final UUID customerId = UUID.randomUUID();

    @Test
    void createsShipmentUsingOnlyTokenSubjectAsOwner() throws Exception {
        var owner = new User("Owner", "owner@example.test", "hash", UserRole.CUSTOMER);
        var quote = quote(owner);
        var input = request(quote.getId());
        var shipment = new Shipment(owner, quote, input.origin().toAddress(), input.destination().toAddress(),
                input.lengthCm(), input.widthCm(), input.heightCm(), QUOTED_AT);
        when(shipments.create(customerId, input)).thenReturn(ShipmentResponse.from(shipment));

        send(json.writeValueAsString(input))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.freightAmount").value(15.00))
                .andExpect(jsonPath("$.weightKg").value(2.0))
                .andExpect(jsonPath("$.quoteId").value(quote.getId().toString()))
                .andExpect(jsonPath("$.origin.contactName").value("Sender"))
                .andExpect(jsonPath("$.destination.contactName").value("Recipient"))
                .andExpect(jsonPath("$.customer").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(shipments).create(customerId, input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"customerId", "freightAmount", "weightKg", "status", "trackingCode"})
    void rejectsServerControlledFields(String field) throws Exception {
        var body = body();
        body.put(field, "arbitrary");
        send(body.toString()).andExpect(status().isBadRequest());
        verifyNoInteractions(shipments);
    }

    @ParameterizedTest
    @ValueSource(strings = {"lengthCm", "widthCm", "heightCm"})
    void rejectsNonpositiveOrOversizedMeasures(String field) throws Exception {
        for (String value : new String[]{"0", "-1", "1000.001"}) {
            var body = body();
            body.put(field, new BigDecimal(value));
            send(body.toString()).andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        verifyNoInteractions(shipments);
    }

    @ParameterizedTest
    @ValueSource(strings = {"origin", "destination"})
    void validatesBothAddressesRecursively(String side) throws Exception {
        for (String[] mutation : new String[][]{
                {"postalCode", "01000-000"}, {"state", "XX"}, {"state", "sp"},
                {"contactName", " "}, {"street", ""}, {"number", ""}, {"neighborhood", ""}, {"city", ""}}) {
            var body = body();
            ((ObjectNode) body.get(side)).put(mutation[0], mutation[1]);
            send(body.toString()).andExpect(status().isBadRequest());
        }
        var body = body();
        body.putNull(side);
        send(body.toString()).andExpect(status().isBadRequest());
        verifyNoInteractions(shipments);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/shipments").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request()))).andExpect(status().isUnauthorized());
        verifyNoInteractions(shipments);
    }

    @Test
    void deniesOperators() throws Exception {
        mvc.perform(post("/api/v1/shipments").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request())))
                .andExpect(status().isForbidden());
        verifyNoInteractions(shipments);
    }

    @Test
    void requiresAWellFormedQuoteId() throws Exception {
        for (String value : new String[]{null, "", "not-a-uuid"}) {
            var body = body();
            if (value == null) {
                body.putNull("quoteId");
            } else {
                body.put("quoteId", value);
            }
            send(body.toString()).andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        verifyNoInteractions(shipments);
    }

    @ParameterizedTest
    @MethodSource("quoteFailures")
    void reportsQuoteFailuresAsProblemDetails(ErrorResponseException failure, int expectedStatus) throws Exception {
        when(shipments.create(any(), any())).thenThrow(failure);
        send(json.writeValueAsString(request())).andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.detail").value(failure.getBody().getDetail()));
    }

    static Stream<Arguments> quoteFailures() {
        return Stream.of(
                Arguments.of(new FreightQuoteNotFoundException(), 404),
                Arguments.of(new FreightQuoteAlreadyUsedException(), 409),
                Arguments.of(new FreightQuoteExpiredException(), 422),
                Arguments.of(new DestinationMismatchException(), 422));
    }

    private ObjectNode body() {
        return (ObjectNode) json.readTree(json.writeValueAsString(request()));
    }

    private org.springframework.test.web.servlet.ResultActions send(String body) throws Exception {
        return mvc.perform(post("/api/v1/shipments")
                .with(jwt().jwt(token -> token.subject(customerId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
