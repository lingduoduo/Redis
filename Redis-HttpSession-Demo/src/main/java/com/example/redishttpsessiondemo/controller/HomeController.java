package com.example.redishttpsessiondemo.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, Object> home(HttpSession session) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Redis HttpSession demo is running");
        result.put("sessionId", session.getId());
        result.put("serverNode", System.getProperty("server.node", "node-default"));
        return result;
    }
}
