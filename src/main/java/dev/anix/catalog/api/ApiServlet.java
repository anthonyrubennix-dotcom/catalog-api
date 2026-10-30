package dev.anix.catalog.api;

import dev.anix.catalog.App;
import dev.anix.catalog.http.ApiException;
import dev.anix.catalog.http.Json;
import dev.anix.catalog.http.Request;
import dev.anix.catalog.http.Response;
import dev.anix.catalog.http.Router;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Front controller for /api/v1/*: routing, content-type checks and a uniform error envelope. */
public class ApiServlet extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(ApiServlet.class);
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT");
    private static final int MAX_BODY = 64 * 1024;

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String path = req.getPathInfo() == null ? "/" : req.getPathInfo();
        try {
            Router.Match match = App.from(getServletContext()).router().match(req.getMethod(), path);
            if (!match.found()) {
                if (!match.allowed().isEmpty()) {
                    res.setHeader("Allow", String.join(", ", match.allowed()));
                    throw new ApiException(405, "method_not_allowed", req.getMethod() + " is not allowed on " + path);
                }
                throw ApiException.notFound("resource " + path);
            }
            byte[] body = readBody(req);
            Request request = new Request(req.getMethod(), path, match.params(), query(req), body);
            write(res, match.handler().handle(request));
        } catch (ApiException e) {
            writeError(res, e);
        } catch (RuntimeException e) {
            LOG.error("unhandled error on {} {}", req.getMethod(), path, e);
            writeError(res, new ApiException(500, "internal_error", "internal server error"));
        }
    }

    private static byte[] readBody(HttpServletRequest req) throws IOException {
        if (!BODY_METHODS.contains(req.getMethod())) return new byte[0];
        String ct = req.getContentType();
        if (ct == null || !ct.toLowerCase().startsWith("application/json")) {
            throw new ApiException(415, "unsupported_media_type", "Content-Type must be application/json");
        }
        if (req.getContentLengthLong() > MAX_BODY) {
            throw new ApiException(413, "payload_too_large", "request body exceeds " + MAX_BODY + " bytes");
        }
        byte[] body = req.getInputStream().readNBytes(MAX_BODY + 1);
        if (body.length > MAX_BODY) {
            throw new ApiException(413, "payload_too_large", "request body exceeds " + MAX_BODY + " bytes");
        }
        return body;
    }

    private static Map<String, String> query(HttpServletRequest req) {
        Map<String, String> q = new HashMap<>();
        req.getParameterMap().forEach((k, v) -> q.put(k, v.length > 0 ? v[0] : ""));
        return q;
    }

    private static void write(HttpServletResponse res, Response r) throws IOException {
        res.setStatus(r.status());
        if (r.body() == null) return;
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        Json.MAPPER.writeValue(res.getOutputStream(), r.body());
    }

    public static void writeError(HttpServletResponse res, ApiException e) throws IOException {
        write(res, new Response(e.status(), Json.error(e.code(), e.getMessage())));
    }
}
