package dev.anix.catalog.books;

import dev.anix.catalog.http.ApiException;

import java.util.Map;

/**
 * Filter, sort and paging options for listing books. Sort fields are mapped through a
 * whitelist, so no client input ever reaches the SQL text.
 */
public record BookQuery(String author, Integer yearFrom, Integer yearTo,
                        String sortColumn, boolean descending, int offset, int limit) {

    public static final int MAX_LIMIT = 100;
    private static final Map<String, String> SORTABLE = Map.of(
            "title", "title", "author", "author", "year", "year", "id", "id");

    /** Parses "year" or "-year" (descending). */
    public static BookQuery of(String author, Integer yearFrom, Integer yearTo,
                               String sort, Integer offset, Integer limit) {
        String s = (sort == null || sort.isBlank()) ? "id" : sort.trim();
        boolean desc = s.startsWith("-");
        String key = desc ? s.substring(1) : s;
        String column = SORTABLE.get(key);
        if (column == null) {
            throw ApiException.badRequest("cannot sort by '" + key + "'; allowed: " + SORTABLE.keySet().stream().sorted().toList());
        }
        if (yearFrom != null && yearTo != null && yearFrom > yearTo) {
            throw ApiException.badRequest("'year_from' must not be after 'year_to'");
        }
        return new BookQuery(author == null || author.isBlank() ? null : author.trim(),
                yearFrom, yearTo, column, desc,
                offset == null ? 0 : offset, limit == null ? 20 : limit);
    }
}
