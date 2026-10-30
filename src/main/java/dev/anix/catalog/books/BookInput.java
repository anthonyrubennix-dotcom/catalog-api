package dev.anix.catalog.books;

import com.fasterxml.jackson.databind.JsonNode;
import dev.anix.catalog.http.ApiException;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Validated input for creating or replacing a book. */
public record BookInput(String isbn, String title, String author, int year) {
    private static final Pattern ISBN13 = Pattern.compile("\\d{13}");
    private static final Set<String> FIELDS = Set.of("isbn", "title", "author", "year");

    public static BookInput from(JsonNode body) {
        if (body == null || !body.isObject()) throw ApiException.badRequest("body must be a JSON object");
        List<String> problems = new ArrayList<>();
        body.fieldNames().forEachRemaining(f -> {
            if (!FIELDS.contains(f)) problems.add("unknown field '" + f + "'");
        });
        String isbn = text(body, "isbn", problems);
        String title = text(body, "title", problems);
        String author = text(body, "author", problems);
        Integer year = null;
        JsonNode y = body.get("year");
        if (y == null || !y.isInt()) problems.add("'year' is required and must be an integer");
        else year = y.intValue();

        if (isbn != null && !ISBN13.matcher(isbn).matches()) problems.add("'isbn' must be 13 digits");
        else if (isbn != null && !checksumOk(isbn)) problems.add("'isbn' has an invalid check digit");
        if (title != null && title.length() > 200) problems.add("'title' is longer than 200 characters");
        if (author != null && author.length() > 120) problems.add("'author' is longer than 120 characters");
        int maxYear = Year.now().getValue() + 1;
        if (year != null && (year < 1450 || year > maxYear)) problems.add("'year' must be between 1450 and " + maxYear);

        if (!problems.isEmpty()) throw ApiException.badRequest(String.join("; ", problems));
        return new BookInput(isbn, title, author, year);
    }

    private static String text(JsonNode body, String field, List<String> problems) {
        JsonNode n = body.get(field);
        if (n == null || !n.isTextual() || n.asText().isBlank()) {
            problems.add("'" + field + "' is required and must be a non-empty string");
            return null;
        }
        return n.asText().trim();
    }

    static boolean checksumOk(String isbn) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int d = isbn.charAt(i) - '0';
            sum += (i % 2 == 0) ? d : 3 * d;
        }
        int check = (10 - sum % 10) % 10;
        return check == isbn.charAt(12) - '0';
    }
}
