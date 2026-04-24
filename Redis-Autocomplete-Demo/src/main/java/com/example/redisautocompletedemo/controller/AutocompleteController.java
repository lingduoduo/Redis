package com.example.redisautocompletedemo.controller;

import com.example.redisautocompletedemo.model.AddSuggestionRequest;
import com.example.redisautocompletedemo.model.AddSuggestionResponse;
import com.example.redisautocompletedemo.model.CompareSuggestResponse;
import com.example.redisautocompletedemo.model.DictionaryInfoResponse;
import com.example.redisautocompletedemo.model.ImportResponse;
import com.example.redisautocompletedemo.model.SuggestResponse;
import com.example.redisautocompletedemo.service.AutocompleteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/autocomplete")
@Validated
public class AutocompleteController {

    private final AutocompleteService autocompleteService;

    public AutocompleteController(AutocompleteService autocompleteService) {
        this.autocompleteService = autocompleteService;
    }

    @PostMapping("/import/sample")
    public ImportResponse importSample(
            @RequestParam(defaultValue = AutocompleteService.DEFAULT_DICTIONARY_KEY) String key,
            @RequestParam(defaultValue = "true") boolean clear,
            @RequestParam(defaultValue = "false") boolean incremental
    ) {
        return autocompleteService.importSample(key, clear, incremental);
    }

    @PostMapping("/suggestions")
    public AddSuggestionResponse addSuggestion(
            @RequestParam(defaultValue = AutocompleteService.DEFAULT_DICTIONARY_KEY) String key,
            @Valid @RequestBody AddSuggestionRequest request
    ) {
        return autocompleteService.addSuggestion(key, request.term(), request.score(), request.incremental());
    }

    @GetMapping("/suggest")
    public SuggestResponse suggest(
            @RequestParam(defaultValue = AutocompleteService.DEFAULT_DICTIONARY_KEY) String key,
            @RequestParam @NotBlank String prefix,
            @RequestParam(defaultValue = "5") @Min(1) @Max(50) int maxResults,
            @RequestParam(defaultValue = "true") boolean fuzzy
    ) {
        return autocompleteService.suggest(key, prefix, maxResults, fuzzy);
    }

    @GetMapping("/suggest/compare")
    public CompareSuggestResponse compare(
            @RequestParam(defaultValue = AutocompleteService.DEFAULT_DICTIONARY_KEY) String key,
            @RequestParam @NotBlank String prefix,
            @RequestParam(defaultValue = "5") @Min(1) @Max(50) int maxResults
    ) {
        return autocompleteService.compare(key, prefix, maxResults);
    }

    @GetMapping("/dictionary")
    public DictionaryInfoResponse dictionaryInfo(
            @RequestParam(defaultValue = AutocompleteService.DEFAULT_DICTIONARY_KEY) String key
    ) {
        return autocompleteService.dictionaryInfo(key);
    }

    @DeleteMapping("/dictionary")
    public DictionaryInfoResponse clearDictionary(
            @RequestParam(defaultValue = AutocompleteService.DEFAULT_DICTIONARY_KEY) String key
    ) {
        return autocompleteService.clearDictionary(key);
    }
}
