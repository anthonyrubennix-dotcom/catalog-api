package dev.anix.catalog.auth;

import dev.anix.catalog.App;
import dev.anix.catalog.http.ApiException;
import dev.anix.catalog.api.ApiServlet;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/** Rejects every request without a valid bearer token, except the public paths. */
public class BearerAuthFilter implements Filter {
    public static final String CLIENT_ATTR = "catalog.client";
    private static final Set<String> PUBLIC_PATHS = Set.of("/health");

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) req;
        String path = http.getPathInfo() == null ? "/" : http.getPathInfo();
        if (PUBLIC_PATHS.contains(path)) {
            chain.doFilter(req, res);
            return;
        }
        App app = App.from(http.getServletContext());
        Optional<String> client = app.clients().authenticate(Tokens.fromHeader(http.getHeader("Authorization")));
        if (client.isEmpty()) {
            HttpServletResponse out = (HttpServletResponse) res;
            out.setHeader("WWW-Authenticate", "Bearer");
            ApiServlet.writeError(out, ApiException.unauthorized());
            return;
        }
        http.setAttribute(CLIENT_ATTR, client.get());
        chain.doFilter(req, res);
    }
}
