package dev.anix.catalog.books;

import dev.anix.catalog.http.ApiException;
import dev.anix.catalog.http.Json;
import dev.anix.catalog.http.Request;
import dev.anix.catalog.http.Response;
import dev.anix.catalog.http.Router;

import java.util.LinkedHashMap;
import java.util.Map;

/** HTTP mapping of the book endpoints. Business rules live in BookInput / BookQuery. */
public final class BookResource {
    private final BookRepository repo;

    public BookResource(BookRepository repo) {
        this.repo = repo;
    }

    public void register(Router router) {
        router.add("GET", "/books", this::list)
              .add("POST", "/books", this::create)
              .add("GET", "/books/{id}", this::get)
              .add("PUT", "/books/{id}", this::replace)
              .add("DELETE", "/books/{id}", this::delete);
    }

    Response list(Request r) {
        BookQuery q = BookQuery.of(
                r.query().get("author"),
                r.queryInt("year_from", null, 1450, 9999),
                r.queryInt("year_to", null, 1450, 9999),
                r.query().get("sort"),
                r.queryInt("offset", 0, 0, Integer.MAX_VALUE),
                r.queryInt("limit", 20, 1, BookQuery.MAX_LIMIT));
        BookRepository.Page page = repo.list(q);
        Map<String, Object> body = Json.ok(page.items());
        Map<String, Object> paging = new LinkedHashMap<>();
        paging.put("offset", q.offset());
        paging.put("limit", q.limit());
        paging.put("total", page.total());
        body.put("page", paging);
        return Response.ok(body);
    }

    Response get(Request r) {
        return Response.ok(Json.ok(repo.find(r.pathLong("id")).orElseThrow(() -> ApiException.notFound("book"))));
    }

    Response create(Request r) {
        return Response.created(Json.ok(repo.create(BookInput.from(Json.parse(r.body())))));
    }

    Response replace(Request r) {
        BookInput in = BookInput.from(Json.parse(r.body()));
        return Response.ok(Json.ok(repo.replace(r.pathLong("id"), in).orElseThrow(() -> ApiException.notFound("book"))));
    }

    Response delete(Request r) {
        if (!repo.delete(r.pathLong("id"))) throw ApiException.notFound("book");
        return Response.noContent();
    }
}
