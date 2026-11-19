package io.github.leonardodvf.deliverytracker;

import io.github.leonardodvf.deliverytracker.freight.service.FreightService;
import io.github.leonardodvf.deliverytracker.shipment.service.ShipmentService;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static io.github.leonardodvf.deliverytracker.shipment.ShipmentTestData.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** End to end over HTTP: the operator moves the shipment and anyone can follow it by code alone. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
@Sql("/db/local/V3__seed_fictional_freight_rates.sql")
class PublicTrackingIntegrationTests extends JwtTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private ShipmentService shipments;
    @Autowired private FreightService freight;
    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;

    @Test
    void anyoneFollowsTheShipmentByCodeWithoutSeeingPersonalData() throws Exception {
        var customer = users.saveAndFlush(new User("Alice Customer", "alice@example.test", "hash", UserRole.CUSTOMER));
        var operator = users.saveAndFlush(new User("Operator", "operator@example.test", "hash", UserRole.OPERATOR));
        var quote = freight.createQuote(customer.getId(), "01000000", new BigDecimal("2"));
        var shipment = shipments.create(customer.getId(), request(quote.getId()));
        entityManager.flush();

        mvc.perform(post("/api/v1/shipments/" + shipment.id() + "/events")
                        .with(jwt().jwt(token -> token.subject(operator.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_OPERATOR")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PICKED_UP\",\"location\":\"Rio hub\"}"))
                .andExpect(status().isCreated());
        entityManager.flush();
        entityManager.clear();

        var body = mvc.perform(get("/api/v1/tracking/" + shipment.trackingCode().toLowerCase(Locale.ROOT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingCode").value(shipment.trackingCode()))
                .andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.destinationCity").value("São Paulo"))
                .andExpect(jsonPath("$.destinationState").value("SP"))
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[0].status").value("CREATED"))
                .andExpect(jsonPath("$.events[1].status").value("PICKED_UP"))
                .andExpect(jsonPath("$.events[1].location").value("Rio hub"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain(shipment.id().toString(), customer.getId().toString(), "Alice", "alice@",
                "Sender", "Recipient", "Destination street", "01000000", "OPERATOR", "freightAmount");
    }

    @Test
    void unknownCodeIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/tracking/" + "A".repeat(32)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Shipment not found"));
    }
}
