package com.example.redishttpsessiondemo.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SharedDraftRequest(
        @NotBlank
        @Size(max = 200)
        String content
) {
}
