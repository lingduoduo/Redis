package com.example.redishttpsessiondemo.controller;

import com.example.redishttpsessiondemo.model.LoginRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerTest {

    private final AuthController authController = new AuthController();

    @AfterEach
    void tearDown() {
        System.clearProperty("server.node");
    }

    @Test
    void loginStoresUserDetailsInHttpSession() {
        System.setProperty("server.node", "node-a");
        MockHttpSession session = new MockHttpSession();

        Map<String, Object> response = authController.login(new LoginRequest("alice"), session);

        assertThat(response)
                .containsEntry("message", "login success")
                .containsEntry("sessionId", session.getId())
                .containsEntry("username", "alice")
                .containsEntry("serverNode", "node-a");
        assertThat(session.getAttribute("loginUser")).isEqualTo("alice");
        assertThat(session.getAttribute("loginTime")).isInstanceOf(String.class);
        assertThat(session.getAttribute("loginTraceId")).isInstanceOf(String.class);
    }

    @Test
    void meReturnsAuthenticatedSessionDetails() {
        MockHttpSession session = new MockHttpSession();
        authController.login(new LoginRequest("alice"), session);

        Map<String, Object> response = authController.me(session);

        assertThat(response)
                .containsEntry("authenticated", true)
                .containsEntry("sessionId", session.getId())
                .containsEntry("username", "alice");
        assertThat(response.get("loginTime")).isInstanceOf(String.class);
        assertThat(response.get("loginTraceId")).isInstanceOf(String.class);
    }

    @Test
    void logoutInvalidatesTheCurrentSession() {
        MockHttpSession session = new MockHttpSession();
        authController.login(new LoginRequest("alice"), session);

        Map<String, Object> response = authController.logout(session);

        assertThat(response)
                .containsEntry("message", "logout success")
                .containsEntry("invalidatedSessionId", session.getId());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void loginRequestRequiresUsername() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(new LoginRequest("")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("username");
    }
}
