package com.example.redisautocompletedemo.controller;

import com.example.redisautocompletedemo.model.AddSuggestionRequest;
import com.example.redisautocompletedemo.model.AddSuggestionResponse;
import com.example.redisautocompletedemo.model.CompareSuggestResponse;
import com.example.redisautocompletedemo.model.DictionaryInfoResponse;
import com.example.redisautocompletedemo.model.ImportResponse;
import com.example.redisautocompletedemo.model.SuggestResponse;
import com.example.redisautocompletedemo.model.SuggestionItem;
import com.example.redisautocompletedemo.service.AutocompleteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutocompleteControllerTest {

    @Mock
    private AutocompleteService autocompleteService;

    @InjectMocks
    private AutocompleteController autocompleteController;

    @Test
    void importSampleDelegatesToService() {
        ImportResponse response = new ImportResponse("sample keywords imported", "keywords_ac", "keywords_counts.txt", 31, 0, false, true, 31);
        when(autocompleteService.importSample("keywords_ac", true, false)).thenReturn(response);

        assertThat(autocompleteController.importSample("keywords_ac", true, false)).isEqualTo(response);
    }

    @Test
    void addSuggestionDelegatesToService() {
        AddSuggestionRequest request = new AddSuggestionRequest("redis docs", 510, false);
        AddSuggestionResponse response = new AddSuggestionResponse("suggestion added", "keywords_ac", "redis docs", 510, false, 32);
        when(autocompleteService.addSuggestion("keywords_ac", "redis docs", 510, false)).thenReturn(response);

        assertThat(autocompleteController.addSuggestion("keywords_ac", request)).isEqualTo(response);
    }

    @Test
    void suggestDelegatesToService() {
        SuggestResponse response = new SuggestResponse("keywords_ac", "redis", true, 5, List.of(new SuggestionItem("redis", 880)));
        when(autocompleteService.suggest("keywords_ac", "redis", 5, true)).thenReturn(response);

        assertThat(autocompleteController.suggest("keywords_ac", "redis", 5, true)).isEqualTo(response);
    }

    @Test
    void compareDelegatesToService() {
        CompareSuggestResponse response = new CompareSuggestResponse(
                "keywords_ac",
                "pythn",
                5,
                List.of(new SuggestionItem("python", 920)),
                List.of(new SuggestionItem("python", 920), new SuggestionItem("pytorch", 600))
        );
        when(autocompleteService.compare("keywords_ac", "pythn", 5)).thenReturn(response);

        assertThat(autocompleteController.compare("keywords_ac", "pythn", 5)).isEqualTo(response);
    }

    @Test
    void dictionaryInfoDelegatesToService() {
        DictionaryInfoResponse response = new DictionaryInfoResponse("dictionary info", "keywords_ac", 31);
        when(autocompleteService.dictionaryInfo("keywords_ac")).thenReturn(response);

        assertThat(autocompleteController.dictionaryInfo("keywords_ac")).isEqualTo(response);
    }
}
