package com.example.redisautocompletedemo.service;

import com.example.redisautocompletedemo.model.AddSuggestionResponse;
import com.example.redisautocompletedemo.model.CompareSuggestResponse;
import com.example.redisautocompletedemo.model.DictionaryInfoResponse;
import com.example.redisautocompletedemo.model.ImportResponse;
import com.example.redisautocompletedemo.model.SuggestResponse;
import com.example.redisautocompletedemo.model.SuggestionItem;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.output.ArrayOutput;
import io.lettuce.core.output.CommandOutput;
import io.lettuce.core.output.IntegerOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.ProtocolKeyword;
import io.lettuce.core.api.sync.RedisCommands;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AutocompleteService {

    public static final String DEFAULT_DICTIONARY_KEY = "keywords_ac";
    public static final String SAMPLE_RESOURCE = "keywords_counts.txt";

    private static final byte[] WITH_SCORES = bytes("WITHSCORES");
    private static final byte[] FUZZY = bytes("FUZZY");
    private static final byte[] MAX = bytes("MAX");
    private static final byte[] INCR = bytes("INCR");
    private static final ProtocolKeyword FT_SUGADD = keyword("FT.SUGADD");
    private static final ProtocolKeyword FT_SUGGET = keyword("FT.SUGGET");
    private static final ProtocolKeyword FT_SUGLEN = keyword("FT.SUGLEN");

    private final StringRedisTemplate redisTemplate;

    public AutocompleteService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public ImportResponse importSample(String key, boolean clearBeforeImport, boolean incremental) {
        String dictionaryKey = normalizeKey(key);
        if (clearBeforeImport) {
            redisTemplate.delete(dictionaryKey);
        }

        List<KeywordEntry> entries = new ArrayList<>();
        int inserted = 0;
        int skipped = 0;
        ClassPathResource resource = new ClassPathResource(SAMPLE_RESOURCE);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                KeywordEntry entry = parseKeywordLine(line);
                if (entry == null) {
                    skipped++;
                } else {
                    entries.add(entry);
                    inserted++;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read bundled sample dataset", e);
        }

        for (KeywordEntry entry : entries) {
            sugadd(dictionaryKey, entry.term(), entry.score(), incremental);
        }

        return new ImportResponse(
                "sample keywords imported",
                dictionaryKey,
                SAMPLE_RESOURCE,
                inserted,
                skipped,
                incremental,
                clearBeforeImport,
                dictionarySize(dictionaryKey)
        );
    }

    public AddSuggestionResponse addSuggestion(String key, String term, double score, boolean incremental) {
        String dictionaryKey = normalizeKey(key);
        sugadd(dictionaryKey, term, score, incremental);
        return new AddSuggestionResponse(
                "suggestion added",
                dictionaryKey,
                term,
                score,
                incremental,
                dictionarySize(dictionaryKey)
        );
    }

    public SuggestResponse suggest(String key, String prefix, int maxResults, boolean fuzzy) {
        String dictionaryKey = normalizeKey(key);
        List<SuggestionItem> suggestions = fetchSuggestions(dictionaryKey, prefix, maxResults, fuzzy);
        return new SuggestResponse(dictionaryKey, prefix, fuzzy, maxResults, suggestions);
    }

    public CompareSuggestResponse compare(String key, String prefix, int maxResults) {
        String dictionaryKey = normalizeKey(key);
        return new CompareSuggestResponse(
                dictionaryKey,
                prefix,
                maxResults,
                fetchSuggestions(dictionaryKey, prefix, maxResults, false),
                fetchSuggestions(dictionaryKey, prefix, maxResults, true)
        );
    }

    public DictionaryInfoResponse dictionaryInfo(String key) {
        String dictionaryKey = normalizeKey(key);
        return new DictionaryInfoResponse("dictionary info", dictionaryKey, dictionarySize(dictionaryKey));
    }

    public DictionaryInfoResponse clearDictionary(String key) {
        String dictionaryKey = normalizeKey(key);
        long before = dictionarySize(dictionaryKey);
        redisTemplate.delete(dictionaryKey);
        return new DictionaryInfoResponse("dictionary cleared", dictionaryKey, before);
    }

    public long dictionarySize(String key) {
        Long size = dispatch(FT_SUGLEN, new IntegerOutput<>(ByteArrayCodec.INSTANCE), bytes(normalizeKey(key)));
        return size == null ? 0L : size;
    }

    private List<SuggestionItem> fetchSuggestions(String key, String prefix, int maxResults, boolean fuzzy) {
        List<byte[]> params = new ArrayList<>();
        params.add(bytes(normalizeKey(key)));
        params.add(bytes(prefix));
        if (fuzzy) {
            params.add(FUZZY);
        }
        params.add(MAX);
        params.add(bytes(Integer.toString(maxResults)));
        params.add(WITH_SCORES);

        Object raw = dispatch(FT_SUGGET, new ArrayOutput<>(ByteArrayCodec.INSTANCE), params.toArray(byte[][]::new));
        return parseSuggestions(raw);
    }

    private List<SuggestionItem> parseSuggestions(Object raw) {
        if (!(raw instanceof List<?> values) || values.isEmpty()) {
            return List.of();
        }
        List<SuggestionItem> suggestions = new ArrayList<>(values.size() / 2);
        for (int i = 0; i + 1 < values.size(); i += 2) {
            String term = toUtf8(values.get(i));
            double score = Double.parseDouble(toUtf8(values.get(i + 1)));
            suggestions.add(new SuggestionItem(term, score));
        }
        return suggestions;
    }

    private byte[][] suggestionAddArgs(String key, String term, double score, boolean incremental) {
        List<byte[]> args = new ArrayList<>();
        args.add(bytes(key));
        args.add(bytes(term));
        args.add(bytes(Double.toString(score)));
        if (incremental) {
            args.add(INCR);
        }
        return args.toArray(byte[][]::new);
    }

    private void sugadd(String key, String term, double score, boolean incremental) {
        dispatch(FT_SUGADD, new IntegerOutput<>(ByteArrayCodec.INSTANCE), suggestionAddArgs(key, term, score, incremental));
    }

    private KeywordEntry parseKeywordLine(String line) {
        String cleaned = line.trim();
        if (cleaned.isEmpty() || cleaned.startsWith("#")) {
            return null;
        }

        String[] parts = cleaned.replace(",", " ").trim().split("\\s+");
        if (parts.length < 2) {
            return null;
        }

        String scoreText = parts[parts.length - 1];
        double score;
        try {
            score = Double.parseDouble(scoreText);
        } catch (NumberFormatException ex) {
            return null;
        }

        int termEnd = cleaned.lastIndexOf(scoreText);
        if (termEnd <= 0) {
            return null;
        }
        String term = cleaned.substring(0, termEnd).replaceAll("[,\\s]+$", "").trim();
        if (term.isEmpty()) {
            return null;
        }
        return new KeywordEntry(term, score);
    }

    private String normalizeKey(String key) {
        return (key == null || key.isBlank()) ? DEFAULT_DICTIONARY_KEY : key;
    }

    private <T> T execute(RedisCallback<T> callback) {
        try {
            return redisTemplate.execute(callback);
        } catch (DataAccessException ex) {
            throw new IllegalStateException(
                    "Redis Stack autocomplete command failed. Make sure Redis Stack is running and RediSearch is available.",
                    ex
            );
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T dispatch(ProtocolKeyword keyword, CommandOutput<byte[], byte[], T> output, byte[]... params) {
        return execute((RedisCallback<T>) connection -> {
            Object nativeConnection = connection.getNativeConnection();
            RedisCommands<byte[], byte[]> redisCommands = resolveRedisCommands(nativeConnection);

            CommandArgs<byte[], byte[]> commandArgs = new CommandArgs<>(ByteArrayCodec.INSTANCE);
            for (byte[] param : params) {
                commandArgs.add(param);
            }

            return redisCommands.dispatch(keyword, output, commandArgs);
        });
    }

    @SuppressWarnings("unchecked")
    private RedisCommands<byte[], byte[]> resolveRedisCommands(Object nativeConnection) {
        if (nativeConnection instanceof RedisCommands<?, ?> syncCommands) {
            return (RedisCommands<byte[], byte[]>) syncCommands;
        }
        if (nativeConnection instanceof RedisAsyncCommands<?, ?> asyncCommands) {
            return (RedisCommands<byte[], byte[]>) asyncCommands.getStatefulConnection().sync();
        }
        throw new IllegalStateException(
                "Unsupported Redis connection type for RediSearch commands: " +
                        (nativeConnection == null ? "null" : nativeConnection.getClass().getName())
        );
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static ProtocolKeyword keyword(String command) {
        byte[] encoded = bytes(command);
        return new ProtocolKeyword() {
            @Override
            public String name() {
                return command;
            }

            @Override
            public byte[] getBytes() {
                return encoded;
            }

            @Override
            public String toString() {
                return command;
            }
        };
    }

    private String toUtf8(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private record KeywordEntry(String term, double score) {
    }
}
