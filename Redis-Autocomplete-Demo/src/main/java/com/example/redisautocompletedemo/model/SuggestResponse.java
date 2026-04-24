package com.example.redisautocompletedemo.model;

import java.util.List;

public record SuggestResponse(
        String dictionaryKey,
        String prefix,
        boolean fuzzy,
        int maxResults,
        List<SuggestionItem> suggestions
) {
}
