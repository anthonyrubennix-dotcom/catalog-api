package dev.anix.catalog.books;

/** A catalogue entry. Serialised with snake_case keys. */
public record Book(long id, String isbn, String title, String author, int year) {}
