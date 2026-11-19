package io.github.leonardodvf.deliverytracker.webhook.controller;

import io.github.leonardodvf.deliverytracker.auth.config.SecurityConfig;
import io.github.leonardodvf.deliverytracker.shared.exception.ApiExceptionHandler;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.github.leonardodvf.deliverytracker.shipment.exception.InvalidStatusTransitionException;
import io.github.leonardodvf.deliverytracker.webhook.dto.WebhookReceipt;
import io.github.leonardodvf.deliverytracker.webhook.exception.InvalidWebhookPayloadException;
import io.github.leonardodvf.deliverytracker.webhook.exception.InvalidWebhookSignatureException;
import io.github.leonardodvf.deliverytracker.webhook.service.CarrierWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CarrierWebhookController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class CarrierWebhookControllerTests {

    private static final String PATH = "/api/v1/webhooks/carrier";
    /** Odd spacing on purpose: the service must receive these exact bytes, or the signature would not match. */
    private static final String BODY = "{ \"eventId\" : \"evt-1\",\n  \"status\":\"PICKED_UP\" }";

    @Autowired private MockMvc mvc;
    @MockitoBean private CarrierWebhookService webhooks;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void passesHeadersAndTheExactRawBodyWithoutRequiringAToken() throws Exception {
        when(webhooks.receive(any(), any(), any())).thenReturn(new WebhookReceipt("evt-1", WebhookReceipt.Outcome.APPLIED));

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("X-Carrier-Timestamp", "1763553600").header("X-Carrier-Signature", "sha256=abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-1"))
                .andExpect(jsonPath("$.outcome").value("APPLIED"));
        verify(webhooks).receive(eq("1763553600"), eq("sha256=abc"), eq(BODY.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void leavesMissingSignatureHeadersToTheServiceSoTheyBecome401() throws Exception {
        when(webhooks.receive(null, null, BODY.getBytes(StandardCharsets.UTF_8)))
                .thenThrow(new InvalidWebhookSignatureException("missing or malformed timestamp"));

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid or missing webhook signature"))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    void reportsPayloadErrorsWithFieldList() throws Exception {
        when(webhooks.receive(any(), any(), any())).thenThrow(InvalidWebhookPayloadException.invalid(
                List.of(new InvalidWebhookPayloadException.FieldError("eventId", "must not be null"))));

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("eventId"));
    }

    @Test
    void reportsOutOfOrderEventsAsConflictSoTheCarrierRetries() throws Exception {
        when(webhooks.receive(any(), any(), any()))
                .thenThrow(new InvalidStatusTransitionException(ShipmentStatus.CREATED, ShipmentStatus.IN_TRANSIT));

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.requestedStatus").value("IN_TRANSIT"));
    }

    @Test
    void rejectsOtherContentTypesEmptyBodiesAndOtherMethods() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.TEXT_PLAIN).content(BODY)).andExpect(status().isUnsupportedMediaType());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(webhooks);
    }
}
