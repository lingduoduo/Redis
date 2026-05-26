package com.example.redishttpsessiondemo.controller;

import com.example.redishttpsessiondemo.model.SharedDraftRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/shared-session")
public class SharedSessionDataController {

    static final String DRAFT_KEY = "sharedDraft";
    static final String DRAFT_UPDATED_AT_KEY = "sharedDraftUpdatedAt";
    static final String COUNTER_KEY = "sharedCounter";

    @GetMapping
    public Map<String, Object> getSharedData(HttpSession session) {
        Map<String, Object> result = baseResponse(session);
        result.put("draft", session.getAttribute(DRAFT_KEY));
        result.put("draftUpdatedAt", session.getAttribute(DRAFT_UPDATED_AT_KEY));
        result.put("counter", currentCounter(session));
        return result;
    }

    @PutMapping("/draft")
    public Map<String, Object> saveDraft(@Valid @RequestBody SharedDraftRequest request, HttpSession session) {
        String updatedAt = Instant.now().toString();
        session.setAttribute(DRAFT_KEY, request.content());
        session.setAttribute(DRAFT_UPDATED_AT_KEY, updatedAt);

        Map<String, Object> result = baseResponse(session);
        result.put("message", "draft saved");
        result.put("draft", request.content());
        result.put("draftUpdatedAt", updatedAt);
        return result;
    }

    @PostMapping("/counter/increment")
    public Map<String, Object> incrementCounter(HttpSession session) {
        int counter = currentCounter(session) + 1;
        session.setAttribute(COUNTER_KEY, counter);

        Map<String, Object> result = baseResponse(session);
        result.put("message", "counter incremented");
        result.put("counter", counter);
        return result;
    }

    @DeleteMapping
    public Map<String, Object> clearSharedData(HttpSession session) {
        session.removeAttribute(DRAFT_KEY);
        session.removeAttribute(DRAFT_UPDATED_AT_KEY);
        session.removeAttribute(COUNTER_KEY);

        Map<String, Object> result = baseResponse(session);
        result.put("message", "shared session data cleared");
        return result;
    }

    private Map<String, Object> baseResponse(HttpSession session) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", session.getId());
        result.put("serverNode", System.getProperty("server.node", "node-default"));
        return result;
    }

    private int currentCounter(HttpSession session) {
        Object value = session.getAttribute(COUNTER_KEY);
        return value instanceof Integer counter ? counter : 0;
    }
}
