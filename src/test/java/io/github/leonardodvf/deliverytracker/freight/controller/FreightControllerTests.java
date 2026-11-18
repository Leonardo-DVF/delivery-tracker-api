package io.github.leonardodvf.deliverytracker.freight.controller;

import io.github.leonardodvf.deliverytracker.auth.config.SecurityConfig;
import io.github.leonardodvf.deliverytracker.freight.entity.FreightQuote;
import io.github.leonardodvf.deliverytracker.freight.exception.ConflictingFreightRatesException;
import io.github.leonardodvf.deliverytracker.freight.exception.FreightRateUnavailableException;
import io.github.leonardodvf.deliverytracker.freight.service.FreightService;
import io.github.leonardodvf.deliverytracker.shared.exception.ApiExceptionHandler;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FreightController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class FreightControllerTests {

    private static final String VALID = "{\"destinationPostalCode\":\"01000000\",\"weightKg\":2}";

    @Autowired private MockMvc mvc;
    @MockitoBean private FreightService freight;
    @MockitoBean private JwtDecoder decoder;
    private final UUID requesterId = UUID.randomUUID();

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "OPERATOR"})
    void storesQuoteForTokenSubjectAndReturnsBackendPriceExpirationAndDisclaimer(String role) throws Exception {
        var requester = new User("Requester", "requester@example.test", "hash", UserRole.valueOf(role));
        var quote = new FreightQuote(requester, "01000000", new BigDecimal("2"), new BigDecimal("15.00"),
                Instant.parse("2026-11-18T12:00:00Z"), Duration.ofMinutes(30));
        when(freight.createQuote(requesterId, "01000000", new BigDecimal("2"))).thenReturn(quote);

        mvc.perform(post("/api/v1/freight/quotes").with(authenticated(role))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quoteId").value(quote.getId().toString()))
                .andExpect(jsonPath("$.amount").value(15.00))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.destinationPostalCode").value("01000000"))
                .andExpect(jsonPath("$.weightKg").value(2))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-18T12:30:00Z"))
                .andExpect(jsonPath("$.disclaimer").value("Fictional demonstration tariff; not a real carrier quote."))
                .andExpect(jsonPath("$.requester").doesNotExist());

        verify(freight).createQuote(requesterId, "01000000", new BigDecimal("2"));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidPostalCodesWeightsAndClientSuppliedPrices(String payload) throws Exception {
        mvc.perform(post("/api/v1/freight/quotes").with(authenticated("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(freight);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/freight/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(freight);
    }

    @Test
    void deniesAnAuthorityOutsideCustomerAndOperator() throws Exception {
        mvc.perform(post("/api/v1/freight/quotes").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());

        verifyNoInteractions(freight);
    }

    @Test
    void returnsUnprocessableContentWhenNoTariffMatches() throws Exception {
        when(freight.createQuote(any(), anyString(), any())).thenThrow(new FreightRateUnavailableException());

        mvc.perform(post("/api/v1/freight/quotes").with(authenticated("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void returnsConflictWhenTariffsAreAmbiguous() throws Exception {
        when(freight.createQuote(any(), anyString(), any())).thenThrow(new ConflictingFreightRatesException());

        mvc.perform(post("/api/v1/freight/quotes").with(authenticated("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Multiple freight tariffs match the destination and weight"));
    }

    private RequestPostProcessor authenticated(String role) {
        return jwt().jwt(token -> token.subject(requesterId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    static Stream<String> invalidRequests() {
        return Stream.of(
                "{}",
                "{\"destinationPostalCode\":\"0100000\",\"weightKg\":2}",
                "{\"destinationPostalCode\":\"01000-000\",\"weightKg\":2}",
                "{\"destinationPostalCode\":\"abcdefgh\",\"weightKg\":2}",
                "{\"destinationPostalCode\":\"010000000\",\"weightKg\":2}",
                "{\"destinationPostalCode\":\" 01000000\",\"weightKg\":2}",
                "{\"destinationPostalCode\":\"01000000\",\"weightKg\":0}",
                "{\"destinationPostalCode\":\"01000000\",\"weightKg\":-1}",
                "{\"destinationPostalCode\":\"01000000\",\"weightKg\":0.0001}",
                "{\"destinationPostalCode\":\"01000000\",\"weightKg\":1.0001}",
                "{\"destinationPostalCode\":\"01000000\",\"weightKg\":1000.001}",
                "{\"destinationPostalCode\":null,\"weightKg\":2}",
                "{\"destinationPostalCode\":\"01000000\",\"weightKg\":null}",
                "{\"destinationPostalCode\":\"01000000\",\"weightKg\":2,\"amount\":0.01}",
                "{\"destinationPostalCode\":\"01000000\",\"weightKg\":2,\"status\":\"DELIVERED\"}");
    }
}
