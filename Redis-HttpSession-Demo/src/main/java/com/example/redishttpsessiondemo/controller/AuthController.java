package com.example.redishttpsessiondemo.controller;

import com.example.redishttpsessiondemo.model.LoginRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        session.setAttribute("loginUser", request.username());
        session.setAttribute("loginTime", Instant.now().toString());
        session.setAttribute("loginTraceId", UUID.randomUUID().toString());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "login success");
        result.put("sessionId", session.getId());
        result.put("username", request.username());
        result.put("serverNode", System.getProperty("server.node", "node-default"));
        return result;
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authenticated", session.getAttribute("loginUser") != null);
        result.put("sessionId", session.getId());
        result.put("username", session.getAttribute("loginUser"));
        result.put("loginTime", session.getAttribute("loginTime"));
        result.put("loginTraceId", session.getAttribute("loginTraceId"));
        result.put("serverNode", System.getProperty("server.node", "node-default"));
        result.put("isNewSession", session.isNew());
        return result;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        String oldSessionId = session.getId();
        session.invalidate();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "logout success");
        result.put("invalidatedSessionId", oldSessionId);
        result.put("serverNode", System.getProperty("server.node", "node-default"));
        return result;
    }
}
