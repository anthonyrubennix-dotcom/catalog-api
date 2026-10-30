package dev.anix.catalog.http;

import java.util.Map;

/** Immutable view of an incoming request after routing. */
public record Request(String method, String path, Map<String, String> pathParams,
                      Map<String, String> query, byte[] body) {

    public String pathParam(String name) {
        return pathParams.get(name);
    }

    public long pathLong(String name) {
        try {
            return Long.parseLong(pathParams.get(name));
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("'" + name + "' must be a number");
        }
    }

    public Integer queryInt(String name, Integer dflt, int min, int max) {
        String v = query.get(name);
        if (v == null || v.isBlank()) return dflt;
        try {
            int i = Integer.parseInt(v.trim());
            if (i < min || i > max) {
                throw ApiException.badRequest("'" + name + "' must be between " + min + " and " + max);
            }
            return i;
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("'" + name + "' must be an integer");
        }
    }
}
