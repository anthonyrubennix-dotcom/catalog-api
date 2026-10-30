package dev.anix.catalog.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Connection pool plus a tiny idempotent schema bootstrap (schema.sql uses IF NOT EXISTS). */
public final class Database {
    private Database() {}

    public static HikariDataSource pool(String url, String user, String password) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(10);
        cfg.setPoolName("catalog");
        return new HikariDataSource(cfg);
    }

    public static void applySchema(DataSource ds) {
        String sql;
        try (InputStream in = Database.class.getResourceAsStream("/schema.sql")) {
            if (in == null) throw new IllegalStateException("schema.sql missing from classpath");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read schema.sql", e);
        }
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            for (String stmt : sql.split(";")) {
                if (!stmt.isBlank()) st.execute(stmt);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("schema bootstrap failed", e);
        }
    }
}
