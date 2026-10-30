package dev.anix.catalog;

import dev.anix.catalog.auth.ApiClients;
import dev.anix.catalog.books.BookRepository;
import dev.anix.catalog.books.BookResource;
import dev.anix.catalog.http.Json;
import dev.anix.catalog.http.Response;
import dev.anix.catalog.http.Router;
import jakarta.servlet.ServletContext;

import javax.sql.DataSource;
import java.util.Map;

/** Wiring of the application: one instance per web application, stored in the ServletContext. */
public final class App {
    static final String ATTR = App.class.getName();

    private final ApiClients clients;
    private final Router router = new Router();

    public App(DataSource ds, String version) {
        this.clients = new ApiClients(ds);
        router.add("GET", "/health", r -> Response.ok(Json.ok(Map.of("status", "up", "version", version))));
        new BookResource(new BookRepository(ds)).register(router);
    }

    public ApiClients clients() { return clients; }
    public Router router() { return router; }

    public static App from(ServletContext ctx) {
        App app = (App) ctx.getAttribute(ATTR);
        if (app == null) throw new IllegalStateException("App not initialised");
        return app;
    }
}
