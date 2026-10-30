package dev.anix.catalog.http;

/** Status code plus body; the body is serialised as JSON, or omitted when null. */
public record Response(int status, Object body) {
    public static Response ok(Object body) { return new Response(200, body); }
    public static Response created(Object body) { return new Response(201, body); }
    public static Response noContent() { return new Response(204, null); }
}
