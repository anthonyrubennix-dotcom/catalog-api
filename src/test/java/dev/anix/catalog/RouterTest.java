package dev.anix.catalog;

import dev.anix.catalog.http.Response;
import dev.anix.catalog.http.Router;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RouterTest {
    private final Router router = new Router()
            .add("GET", "/books", r -> Response.ok("list"))
            .add("GET", "/books/{id}", r -> Response.ok(r.pathParam("id")))
            .add("DELETE", "/books/{id}", r -> Response.noContent());

    @Test
    void matchesStaticAndPlaceholderRoutes() {
        assertTrue(router.match("GET", "/books").found());
        Router.Match m = router.match("GET", "/books/42");
        assertTrue(m.found());
        assertEquals("42", m.params().get("id"));
    }

    @Test
    void toleratesTrailingSlash() {
        assertTrue(router.match("GET", "/books/").found());
    }

    @Test
    void reportsAllowedMethodsForKnownPath() {
        Router.Match m = router.match("PATCH", "/books/7");
        assertFalse(m.found());
        assertEquals(Set.of("GET", "DELETE"), m.allowed());
    }

    @Test
    void placeholderDoesNotCrossSegmentsOrAcceptTraversal() {
        assertFalse(router.match("GET", "/books/1/2").found());
        assertFalse(router.match("GET", "/books/..%2F").found());
        assertTrue(router.match("GET", "/nothing").allowed().isEmpty());
    }
}
