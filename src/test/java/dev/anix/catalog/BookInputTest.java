package dev.anix.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import dev.anix.catalog.books.BookInput;
import dev.anix.catalog.books.BookQuery;
import dev.anix.catalog.http.ApiException;
import dev.anix.catalog.http.Json;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BookInputTest {
    private static JsonNode json(String s) {
        return Json.parse(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void acceptsValidBook() {
        BookInput in = BookInput.from(json("""
                {"isbn":"9780134685991","title":" Effective Java ","author":"Joshua Bloch","year":2018}"""));
        assertEquals("Effective Java", in.title());
        assertEquals(2018, in.year());
    }

    @Test
    void collectsAllProblemsInOneMessage() {
        ApiException e = assertThrows(ApiException.class, () -> BookInput.from(json("""
                {"isbn":"123","title":"","year":"x","extra":1}""")));
        assertEquals(400, e.status());
        assertTrue(e.getMessage().contains("unknown field 'extra'"));
        assertTrue(e.getMessage().contains("'title' is required"));
        assertTrue(e.getMessage().contains("'author' is required"));
        assertTrue(e.getMessage().contains("'year' is required"));
        assertTrue(e.getMessage().contains("13 digits"));
    }

    @Test
    void rejectsWrongIsbnCheckDigit() {
        ApiException e = assertThrows(ApiException.class, () -> BookInput.from(json("""
                {"isbn":"9780134685990","title":"T","author":"A","year":2018}""")));
        assertTrue(e.getMessage().contains("check digit"));
    }

    @Test
    void rejectsInvalidJson() {
        assertThrows(ApiException.class, () -> Json.parse("{nope".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void sortIsWhitelisted() {
        BookQuery q = BookQuery.of(null, null, null, "-year", null, null);
        assertEquals("year", q.sortColumn());
        assertTrue(q.descending());
        ApiException e = assertThrows(ApiException.class,
                () -> BookQuery.of(null, null, null, "year; DROP TABLE book", null, null));
        assertEquals(400, e.status());
    }

    @Test
    void yearRangeMustBeOrdered() {
        assertThrows(ApiException.class, () -> BookQuery.of(null, 2000, 1990, null, null, null));
    }
}
