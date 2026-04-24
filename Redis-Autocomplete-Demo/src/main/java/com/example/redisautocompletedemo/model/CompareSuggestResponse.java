package com.example.redisautocompletedemo.model;

import java.util.List;

public record CompareSuggestResponse(
        String dictionaryKey,
        String prefix,
        int maxResults,
        List<SuggestionItem> exactSuggestions,
        List<SuggestionItem> fuzzySuggestions
) {
}
