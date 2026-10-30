package dev.anix.catalog.http;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared JSON mapper and the response envelope used by every endpoint. */
public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private Json() {}

    public static JsonNode parse(byte[] body) {
        if (body == null || body.length == 0) {
            throw ApiException.badRequest("request body is required");
        }
        try {
            return MAPPER.readTree(body);
        } catch (IOException e) {
            throw ApiException.badRequest("request body is not valid JSON");
        }
    }

    public static Map<String, Object> ok(Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", data);
        return m;
    }

    public static Map<String, Object> error(String code, String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        return Map.of("error", err);
    }
}
