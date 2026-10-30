package dev.anix.catalog.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal method + path router. Patterns use {name} placeholders that match one path segment
 * made of letters, digits, '-' and '_'.
 */
public final class Router {

    @FunctionalInterface
    public interface Handler {
        Response handle(Request request);
    }

    private record Route(String method, Pattern pattern, List<String> names, Handler handler) {}

    /** Result of a lookup: either a handler with its params, or the set of allowed methods (405). */
    public record Match(Handler handler, Map<String, String> params, Set<String> allowed) {
        public boolean found() { return handler != null; }
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");
    private final List<Route> routes = new ArrayList<>();

    public Router add(String method, String template, Handler handler) {
        List<String> names = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder regex = new StringBuilder("^");
        int last = 0;
        while (m.find()) {
            regex.append(Pattern.quote(template.substring(last, m.start())));
            regex.append("([A-Za-z0-9_-]+)");
            names.add(m.group(1));
            last = m.end();
        }
        regex.append(Pattern.quote(template.substring(last))).append("/?$");
        routes.add(new Route(method, Pattern.compile(regex.toString()), names, handler));
        return this;
    }

    public Match match(String method, String path) {
        Set<String> allowed = new LinkedHashSet<>();
        for (Route r : routes) {
            Matcher m = r.pattern().matcher(path);
            if (!m.matches()) continue;
            if (!r.method().equals(method)) {
                allowed.add(r.method());
                continue;
            }
            Map<String, String> params = new HashMap<>();
            for (int i = 0; i < r.names().size(); i++) {
                params.put(r.names().get(i), m.group(i + 1));
            }
            return new Match(r.handler(), params, Set.of());
        }
        return new Match(null, Map.of(), allowed);
    }
}
