package com.example.redisautocompletedemo.service;

import com.example.redisautocompletedemo.model.CompareSuggestResponse;
import com.example.redisautocompletedemo.model.DictionaryInfoResponse;
import com.example.redisautocompletedemo.model.ImportResponse;
import com.example.redisautocompletedemo.model.SuggestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutocompleteServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisConnection redisConnection;

    private AutocompleteService autocompleteService;

    @BeforeEach
    void setUp() {
        autocompleteService = new AutocompleteService(redisTemplate);
    }

    @Test
    void suggestReturnsParsedSuggestionRows() {
        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenAnswer(invocation -> {
                    RedisCallback<?> callback = invocation.getArgument(0);
                    return callback.doInRedis(redisConnection);
                });
        when(redisConnection.execute(eq("FT.SUGGET"), any(byte[][].class)))
                .thenReturn(List.of(
                        bytes("redis"), bytes("880"),
                        bytes("redis stack"), bytes("760")
                ));

        SuggestResponse response = autocompleteService.suggest("keywords_ac", "redis", 5, true);

        assertThat(response.suggestions()).hasSize(2);
        assertThat(response.suggestions().get(0).term()).isEqualTo("redis");
        assertThat(response.suggestions().get(0).score()).isEqualTo(880.0);
        assertThat(response.suggestions().get(1).term()).isEqualTo("redis stack");
        assertThat(response.fuzzy()).isTrue();
    }

    @Test
    void compareReturnsExactAndFuzzyVariants() {
        when(redisTemplate.executePipelined(any(RedisCallback.class)))
                .thenReturn(List.of(
                        List.of(bytes("python"), bytes("920")),
                        List.of(bytes("python"), bytes("920"), bytes("pytorch"), bytes("600"))
                ));

        CompareSuggestResponse response = autocompleteService.compare("keywords_ac", "pythn", 5);

        assertThat(response.exactSuggestions()).hasSize(1);
        assertThat(response.fuzzySuggestions()).hasSize(2);
    }

    @Test
    void dictionaryInfoReadsDictionaryLength() {
        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenAnswer(invocation -> {
                    RedisCallback<?> callback = invocation.getArgument(0);
                    return callback.doInRedis(redisConnection);
                });
        when(redisConnection.execute(eq("FT.SUGLEN"), any(byte[][].class))).thenReturn(31L);

        DictionaryInfoResponse response = autocompleteService.dictionaryInfo("keywords_ac");

        assertThat(response.size()).isEqualTo(31L);
        assertThat(response.dictionaryKey()).isEqualTo("keywords_ac");
    }

    @Test
    void clearDictionaryDeletesKeyAndReturnsPreviousSize() {
        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenAnswer(invocation -> {
                    RedisCallback<?> callback = invocation.getArgument(0);
                    return callback.doInRedis(redisConnection);
                });
        when(redisConnection.execute(eq("FT.SUGLEN"), any(byte[][].class))).thenReturn(31L);

        DictionaryInfoResponse response = autocompleteService.clearDictionary("keywords_ac");

        verify(redisTemplate).delete("keywords_ac");
        assertThat(response.size()).isEqualTo(31L);
    }

    @Test
    void importSampleReportsInsertedRows() {
        when(redisTemplate.executePipelined(any(RedisCallback.class)))
                .thenAnswer(invocation -> {
                    RedisCallback<?> callback = invocation.getArgument(0);
                    callback.doInRedis(redisConnection);
                    return List.of(31L); // last element is FT.SUGLEN result from the pipeline
                });

        ImportResponse response = autocompleteService.importSample("keywords_ac", true, false);

        verify(redisTemplate).delete("keywords_ac");
        verify(redisTemplate).executePipelined(any(RedisCallback.class));
        assertThat(response.inserted()).isEqualTo(31);
        assertThat(response.skipped()).isZero();
        assertThat(response.clearedBeforeImport()).isTrue();
        assertThat(response.dictionarySize()).isEqualTo(31L);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
