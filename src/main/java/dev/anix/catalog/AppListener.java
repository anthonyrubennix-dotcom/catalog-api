package dev.anix.catalog;

import com.zaxxer.hikari.HikariDataSource;
import dev.anix.catalog.api.ApiServlet;
import dev.anix.catalog.auth.BearerAuthFilter;
import dev.anix.catalog.db.Database;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRegistration;

/**
 * Boots the application: connection pool, schema, routes, servlet and auth filter.
 * Settings: context init params catalog.db.url / .user / .password, falling back to the
 * environment variables CATALOG_DB_URL / CATALOG_DB_USER / CATALOG_DB_PASSWORD.
 */
public class AppListener implements ServletContextListener {
    private HikariDataSource pool;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext ctx = event.getServletContext();
        pool = Database.pool(setting(ctx, "url"), setting(ctx, "user"), setting(ctx, "password"));
        Database.applySchema(pool);
        ctx.setAttribute(App.ATTR, new App(pool, "1.0.0"));

        ServletRegistration.Dynamic servlet = ctx.addServlet("api", new ApiServlet());
        servlet.addMapping("/api/v1/*");
        FilterRegistration.Dynamic auth = ctx.addFilter("bearer-auth", new BearerAuthFilter());
        auth.addMappingForServletNames(null, false, "api");
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (pool != null) pool.close();
    }

    private static String setting(ServletContext ctx, String key) {
        String v = ctx.getInitParameter("catalog.db." + key);
        if (v == null) v = System.getenv("CATALOG_DB_" + key.toUpperCase());
        if (v == null) throw new IllegalStateException("missing setting catalog.db." + key);
        return v;
    }
}
