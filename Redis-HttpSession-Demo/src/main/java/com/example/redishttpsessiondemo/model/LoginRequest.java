package com.example.redishttpsessiondemo.model;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String username) {
}
