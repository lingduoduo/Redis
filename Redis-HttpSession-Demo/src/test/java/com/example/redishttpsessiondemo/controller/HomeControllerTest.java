package com.example.redishttpsessiondemo.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

class HomeControllerTest {

    private final HomeController homeController = new HomeController();

    @AfterEach
    void tearDown() {
        System.clearProperty("server.node");
    }

    @Test
    void homeReturnsSessionAndNodeDetails() {
        System.setProperty("server.node", "node-b");
        MockHttpSession session = new MockHttpSession();

        assertThat(homeController.home(session))
                .containsEntry("message", "Redis HttpSession demo is running")
                .containsEntry("sessionId", session.getId())
                .containsEntry("serverNode", "node-b");
    }
}
