package dev.anix.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import dev.anix.catalog.http.Json;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end: real MySQL (Testcontainers) + embedded Tomcat running the same listener,
 * servlet and filter as the WAR. Requests go over real HTTP.
 */
@Testcontainers
class CatalogApiIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    static Tomcat tomcat;
    static String base;
    static String token;
    static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void start() throws Exception {
        Path baseDir = Files.createTempDirectory("tomcat");
        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(0);
        Context ctx = tomcat.addContext("", baseDir.toString());
        ctx.addParameter("catalog.db.url", MYSQL.getJdbcUrl());
        ctx.addParameter("catalog.db.user", MYSQL.getUsername());
        ctx.addParameter("catalog.db.password", MYSQL.getPassword());
        ctx.addApplicationListener(AppListener.class.getName());
        tomcat.getConnector();
        tomcat.start();
        base = "http://localhost:" + tomcat.getConnector().getLocalPort() + "/api/v1";
        token = App.from(ctx.getServletContext()).clients().create("integration-test");
    }

    @AfterAll
    static void stop() throws Exception {
        if (tomcat != null) {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    @BeforeEach
    void cleanBooks() throws Exception {
        try (Connection c = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement st = c.createStatement()) {
            st.execute("DELETE FROM book");
        }
    }

    // ---- helpers -------------------------------------------------------------------------

    record Result(int status, JsonNode body, HttpResponse<String> raw) {}

    static Result call(String method, String path, String json, String bearer) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        if (json != null) {
            b.header("Content-Type", "application/json");
            b.method(method, HttpRequest.BodyPublishers.ofString(json));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode body = res.body().isEmpty() ? null : Json.MAPPER.readTree(res.body());
        return new Result(res.statusCode(), body, res);
    }

    static Result call(String method, String path, String json) throws IOException, InterruptedException {
        return call(method, path, json, token);
    }

    static long createBook(String isbn, String title, String author, int year) throws Exception {
        Result r = call("POST", "/books", """
                {"isbn":"%s","title":"%s","author":"%s","year":%d}""".formatted(isbn, title, author, year));
        assertEquals(201, r.status(), r.raw().body());
        return r.body().at("/data/id").asLong();
    }

    // ---- tests ---------------------------------------------------------------------------

    @Test
    void healthIsPublic() throws Exception {
        Result r = call("GET", "/health", null, null);
        assertEquals(200, r.status());
        assertEquals("up", r.body().at("/data/status").asText());
    }

    @Test
    void everythingElseNeedsAValidToken() throws Exception {
        Result missing = call("GET", "/books", null, null);
        assertEquals(401, missing.status());
        assertEquals("unauthorized", missing.body().at("/error/code").asText());
        assertEquals("Bearer", missing.raw().headers().firstValue("WWW-Authenticate").orElse(""));
        assertEquals(401, call("GET", "/books", null, "wrong-token").status());
    }

    @Test
    void revokedTokenIsRejected() throws Exception {
        App app = App.from(((Context) tomcat.getHost().findChild("")).getServletContext());
        String t = app.clients().create("short-lived");
        assertEquals(200, call("GET", "/books", null, t).status());
        app.clients().revoke("short-lived");
        assertEquals(401, call("GET", "/books", null, t).status());
    }

    @Test
    void createReadReplaceDelete() throws Exception {
        long id = createBook("9780134685991", "Effective Java", "Joshua Bloch", 2018);

        Result got = call("GET", "/books/" + id, null);
        assertEquals(200, got.status());
        assertEquals("Effective Java", got.body().at("/data/title").asText());
        assertEquals("application/json;charset=UTF-8", got.raw().headers().firstValue("Content-Type").orElse(""));

        Result put = call("PUT", "/books/" + id, """
                {"isbn":"9780134685991","title":"Effective Java, 3rd ed.","author":"Joshua Bloch","year":2018}""");
        assertEquals(200, put.status());
        assertEquals("Effective Java, 3rd ed.", call("GET", "/books/" + id, null).body().at("/data/title").asText());

        assertEquals(204, call("DELETE", "/books/" + id, null).status());
        Result gone = call("GET", "/books/" + id, null);
        assertEquals(404, gone.status());
        assertEquals("not_found", gone.body().at("/error/code").asText());
    }

    @Test
    void duplicateIsbnIsAConflict() throws Exception {
        createBook("9780134685991", "Effective Java", "Joshua Bloch", 2018);
        Result r = call("POST", "/books", """
                {"isbn":"9780134685991","title":"Copy","author":"Someone","year":2020}""");
        assertEquals(409, r.status());
    }

    @Test
    void validationErrorsAre400AndListEveryProblem() throws Exception {
        Result r = call("POST", "/books", """
                {"isbn":"1","title":"","author":"A","year":1200}""");
        assertEquals(400, r.status());
        String msg = r.body().at("/error/message").asText();
        assertTrue(msg.contains("13 digits") && msg.contains("'title'") && msg.contains("'year'"), msg);
    }

    @Test
    void wrongContentTypeIs415AndWrongMethodIs405() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/books"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("hello")).build();
        assertEquals(415, HTTP.send(req, HttpResponse.BodyHandlers.ofString()).statusCode());

        Result r = call("PATCH", "/books/1", "{}");
        assertEquals(405, r.status());
        assertTrue(r.raw().headers().firstValue("Allow").orElse("").contains("PUT"));
    }

    @Test
    void listFiltersSortsAndPages() throws Exception {
        createBook("9780134685991", "Effective Java", "Joshua Bloch", 2018);
        createBook("9780321349606", "Java Concurrency in Practice", "Brian Goetz", 2006);
        createBook("9780596009205", "Head First Java", "Kathy Sierra", 2005);
        createBook("9780132350884", "Clean Code", "Robert C. Martin", 2008);

        Result all = call("GET", "/books?sort=-year&limit=2", null);
        assertEquals(200, all.status());
        assertEquals(4, all.body().at("/page/total").asLong());
        assertEquals(2, all.body().at("/data").size());
        assertEquals(2018, all.body().at("/data/0/year").asInt());
        assertEquals(2008, all.body().at("/data/1/year").asInt());

        Result page2 = call("GET", "/books?sort=-year&limit=2&offset=2", null);
        assertEquals(2006, page2.body().at("/data/0/year").asInt());

        Result range = call("GET", "/books?year_from=2005&year_to=2006&sort=title", null);
        assertEquals(2, range.body().at("/page/total").asLong());
        assertEquals("Head First Java", range.body().at("/data/0/title").asText());

        Result byAuthor = call("GET", "/books?author=Brian%20Goetz", null);
        assertEquals(1, byAuthor.body().at("/page/total").asLong());
    }

    @Test
    void badQueryParametersAre400NotSqlErrors() throws Exception {
        assertEquals(400, call("GET", "/books?sort=title;DROP%20TABLE%20book", null).status());
        assertEquals(400, call("GET", "/books?limit=1000", null).status());
        assertEquals(400, call("GET", "/books?limit=abc", null).status());
        assertEquals(400, call("GET", "/books/abc", null).status());
        assertEquals(200, call("GET", "/books", null).status());
    }

    @Test
    void injectionAttemptInFilterIsTreatedAsPlainValue() throws Exception {
        createBook("9780134685991", "Effective Java", "Joshua Bloch", 2018);
        Result r = call("GET", "/books?author=x'%20OR%20'1'='1", null);
        assertEquals(200, r.status());
        assertEquals(0, r.body().at("/page/total").asLong());
    }
}
