package dev.anix.catalog.auth;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Registered API clients. Tokens are looked up by hash; revoked clients are ignored. */
public final class ApiClients {
    private final DataSource ds;

    public ApiClients(DataSource ds) {
        this.ds = ds;
    }

    /** Creates a client and returns its token. The plain token is not stored anywhere. */
    public String create(String name) {
        String token = Tokens.newToken();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO api_client (name, token_sha256) VALUES (?, ?)")) {
            ps.setString(1, name);
            ps.setString(2, Tokens.hash(token));
            ps.executeUpdate();
            return token;
        } catch (SQLException e) {
            throw new IllegalStateException("could not create api client", e);
        }
    }

    public Optional<String> authenticate(String token) {
        if (token == null) return Optional.empty();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name FROM api_client WHERE token_sha256 = ? AND revoked_at IS NULL")) {
            ps.setString(1, Tokens.hash(token));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not check api client", e);
        }
    }

    public void revoke(String name) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE api_client SET revoked_at = CURRENT_TIMESTAMP WHERE name = ?")) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("could not revoke api client", e);
        }
    }
}
