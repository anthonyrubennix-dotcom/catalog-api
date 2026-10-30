package dev.anix.catalog.books;

import dev.anix.catalog.http.ApiException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Plain JDBC with prepared statements only. */
public final class BookRepository {
    private static final String COLUMNS = "id, isbn, title, author, year";
    private final DataSource ds;

    public BookRepository(DataSource ds) {
        this.ds = ds;
    }

    public record Page(List<Book> items, long total) {}

    public Page list(BookQuery q) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (q.author() != null) { where.append(" AND author = ?"); args.add(q.author()); }
        if (q.yearFrom() != null) { where.append(" AND year >= ?"); args.add(q.yearFrom()); }
        if (q.yearTo() != null) { where.append(" AND year <= ?"); args.add(q.yearTo()); }
        // sortColumn comes from a fixed whitelist in BookQuery, never from raw input
        String order = " ORDER BY " + q.sortColumn() + (q.descending() ? " DESC" : " ASC") + ", id ASC";

        try (Connection c = ds.getConnection()) {
            long total;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM book" + where)) {
                bind(ps, args);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); total = rs.getLong(1); }
            }
            List<Book> items = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT " + COLUMNS + " FROM book" + where + order + " LIMIT ? OFFSET ?")) {
                List<Object> paged = new ArrayList<>(args);
                paged.add(q.limit());
                paged.add(q.offset());
                bind(ps, paged);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) items.add(map(rs));
                }
            }
            return new Page(items, total);
        } catch (SQLException e) {
            throw new IllegalStateException("list books failed", e);
        }
    }

    public Optional<Book> find(long id) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM book WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("find book failed", e);
        }
    }

    public Book create(BookInput in) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO book (isbn, title, author, year) VALUES (?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, in.isbn());
            ps.setString(2, in.title());
            ps.setString(3, in.author());
            ps.setInt(4, in.year());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Book(keys.getLong(1), in.isbn(), in.title(), in.author(), in.year());
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw ApiException.conflict("a book with isbn " + in.isbn() + " already exists");
        } catch (SQLException e) {
            throw new IllegalStateException("create book failed", e);
        }
    }

    public Optional<Book> replace(long id, BookInput in) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE book SET isbn = ?, title = ?, author = ?, year = ? WHERE id = ?")) {
            ps.setString(1, in.isbn());
            ps.setString(2, in.title());
            ps.setString(3, in.author());
            ps.setInt(4, in.year());
            ps.setLong(5, id);
            if (ps.executeUpdate() == 0 && find(id).isEmpty()) return Optional.empty();
            return Optional.of(new Book(id, in.isbn(), in.title(), in.author(), in.year()));
        } catch (SQLIntegrityConstraintViolationException e) {
            throw ApiException.conflict("a book with isbn " + in.isbn() + " already exists");
        } catch (SQLException e) {
            throw new IllegalStateException("update book failed", e);
        }
    }

    public boolean delete(long id) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM book WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("delete book failed", e);
        }
    }

    private static void bind(PreparedStatement ps, List<Object> args) throws SQLException {
        for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
    }

    private static Book map(ResultSet rs) throws SQLException {
        return new Book(rs.getLong("id"), rs.getString("isbn"), rs.getString("title"),
                rs.getString("author"), rs.getInt("year"));
    }
}
