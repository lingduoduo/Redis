package com.example.redishttpsessiondemo.controller;

import com.example.redishttpsessiondemo.model.SharedDraftRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SharedSessionDataControllerTest {

    private final SharedSessionDataController controller = new SharedSessionDataController();

    @AfterEach
    void tearDown() {
        System.clearProperty("server.node");
    }

    @Test
    void saveDraftStoresShortLivedSharedDataInSession() {
        System.setProperty("server.node", "node-a");
        MockHttpSession session = new MockHttpSession();

        Map<String, Object> response = controller.saveDraft(new SharedDraftRequest("checkout step 2"), session);

        assertThat(response)
                .containsEntry("message", "draft saved")
                .containsEntry("sessionId", session.getId())
                .containsEntry("serverNode", "node-a")
                .containsEntry("draft", "checkout step 2");
        assertThat(response.get("draftUpdatedAt")).isInstanceOf(String.class);
        assertThat(session.getAttribute(SharedSessionDataController.DRAFT_KEY)).isEqualTo("checkout step 2");
    }

    @Test
    void getSharedDataReadsSameSessionOnAnotherNode() {
        MockHttpSession session = new MockHttpSession();

        System.setProperty("server.node", "node-a");
        controller.saveDraft(new SharedDraftRequest("cart draft from node-a"), session);
        controller.incrementCounter(session);

        System.setProperty("server.node", "node-b");
        Map<String, Object> response = controller.getSharedData(session);

        assertThat(response)
                .containsEntry("sessionId", session.getId())
                .containsEntry("serverNode", "node-b")
                .containsEntry("draft", "cart draft from node-a")
                .containsEntry("counter", 1);
        assertThat(response.get("draftUpdatedAt")).isInstanceOf(String.class);
    }

    @Test
    void incrementCounterUpdatesSharedSessionValue() {
        MockHttpSession session = new MockHttpSession();

        assertThat(controller.incrementCounter(session)).containsEntry("counter", 1);
        assertThat(controller.incrementCounter(session)).containsEntry("counter", 2);
        assertThat(controller.getSharedData(session)).containsEntry("counter", 2);
    }

    @Test
    void clearSharedDataRemovesSessionAttributes() {
        MockHttpSession session = new MockHttpSession();
        controller.saveDraft(new SharedDraftRequest("temporary data"), session);
        controller.incrementCounter(session);

        Map<String, Object> response = controller.clearSharedData(session);

        assertThat(response).containsEntry("message", "shared session data cleared");
        assertThat(session.getAttribute(SharedSessionDataController.DRAFT_KEY)).isNull();
        assertThat(session.getAttribute(SharedSessionDataController.DRAFT_UPDATED_AT_KEY)).isNull();
        assertThat(session.getAttribute(SharedSessionDataController.COUNTER_KEY)).isNull();
        assertThat(controller.getSharedData(session))
                .containsEntry("draft", null)
                .containsEntry("draftUpdatedAt", null)
                .containsEntry("counter", 0);
    }

    @Test
    void sharedDraftRequestRequiresContent() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(new SharedDraftRequest("")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("content");
    }
}
