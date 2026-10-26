package io.github.leonardodvf.deliverytracker.shared.config;

import io.github.leonardodvf.deliverytracker.auth.service.LoginService;
import io.github.leonardodvf.deliverytracker.auth.service.RegistrationService;
import io.github.leonardodvf.deliverytracker.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = OpenApiDocumentationTests.TestApplication.class, properties = "API_DOCS_ENABLED=false")
@AutoConfigureMockMvc
class DisabledOpenApiDocumentationTests {

    @Autowired private MockMvc mvc;
    @MockitoBean private RegistrationService registration;
    @MockitoBean private LoginService login;
    @MockitoBean private UserService users;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void disablesDocumentationAndUiTogether() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }
}
