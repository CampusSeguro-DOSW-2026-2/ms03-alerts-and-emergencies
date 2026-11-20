package edu.eci.dosw.campusseguro.alertas.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.eci.dosw.campusseguro.alertas.dto.response.AlertFilterGroupResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertFilterOptionResponse;
import edu.eci.dosw.campusseguro.alertas.service.AlertFilterCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Prueba el endpoint HTTP y la autorización por método. {@code status} se selecciona una sola vez;
 * {@code type} es múltiple. El endpoint comparte el permiso del listado: {@code read_history}.
 */
@WebMvcTest(controllers = AlertFilterController.class)
@Import({AlertFilterController.class, AlertFilterControllerTest.TestSecurityConfig.class})
class AlertFilterControllerTest {

    private static final String URL = "/api/v1/alertas/filtros";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlertFilterCatalog alertFilterCatalog;

    @Test
    void list_shouldReturnCatalog_whenUserCanReadHistory() throws Exception {
        when(alertFilterCatalog.groups()).thenReturn(List.of(
                new AlertFilterGroupResponse("status", "Estado", false, List.of(
                        new AlertFilterOptionResponse("ACTIVE", "Activa", false),
                        new AlertFilterOptionResponse("ARCHIVED", "Archivada", true))),
                new AlertFilterGroupResponse("type", "Tipo", true, List.of(
                        new AlertFilterOptionResponse("SISMO", "Sismo", null)))));

        mockMvc.perform(get(URL).with(authentication(authenticationWith("ms03:alert:read_history"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups", hasSize(2)))
                .andExpect(jsonPath("$.groups[0].key").value("status"))
                .andExpect(jsonPath("$.groups[0].label").value("Estado"))
                .andExpect(jsonPath("$.groups[0].multiple").value(false))
                .andExpect(jsonPath("$.groups[0].options", hasSize(2)))
                .andExpect(jsonPath("$.groups[0].options[0].value").value("ACTIVE"))
                .andExpect(jsonPath("$.groups[0].options[0].label").value("Activa"))
                .andExpect(jsonPath("$.groups[0].options[0].finalState").value(false))
                .andExpect(jsonPath("$.groups[0].options[1].finalState").value(true))
                .andExpect(jsonPath("$.groups[1].key").value("type"))
                .andExpect(jsonPath("$.groups[1].label").value("Tipo"))
                .andExpect(jsonPath("$.groups[1].multiple").value(true))
                .andExpect(jsonPath("$.groups[1].options[0].value").value("SISMO"))
                .andExpect(jsonPath("$.groups[1].options[0].finalState").value(nullValue()));
    }

    @Test
    void list_shouldReturn401_whenRequestIsUnauthenticated() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(alertFilterCatalog);
    }

    @Test
    void list_shouldReturn403_whenUserLacksReadHistory() throws Exception {
        mockMvc.perform(get(URL).with(authentication(authenticationWith("ms03:alert:receive"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(alertFilterCatalog);
    }

    private static Authentication authenticationWith(String authority) {
        return UsernamePasswordAuthenticationToken.authenticated(
                "auditor",
                null,
                List.of(new SimpleGrantedAuthority(authority)));
    }

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize
                            .anyRequest().authenticated())
                    .exceptionHandling(handling -> handling
                            .authenticationEntryPoint(
                                    (request, response, exception) -> response.setStatus(401))
                            .accessDeniedHandler(
                                    (request, response, exception) -> response.setStatus(403)));
            return http.build();
        }
    }
}
