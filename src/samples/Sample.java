package gwu.samples;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A sample library management class demonstrating various Java features.
 * Used as the default input for the REJD AST parser.
 *
 * Covers: class inheritance, interface implementation, static/instance fields,
 * generics, constructors, various method signatures, inner classes,
 * control flow, and exception handling.
 */
public class Library extends AbstractRepository implements Searchable, Serializable {

    // ---- Static Fields ----
    private static final long serialVersionUID = 1L;
    public static final int MAX_BOOKS = 1000;
    private static int instanceCount = 0;

    // ---- Instance Fields ----
    private final String name;
    private final String location;
    private List<Book> books;
    private boolean open;

    // ---- Constructor ----
    public Library(String name, String location) {
        this.name = name;
        this.location = location;
        this.books = new ArrayList<>();
        this.open = true;
        instanceCount++;
    }

    // ---- Instance Methods ----

    /**
     * Adds a book to the library if capacity allows.
     *
     * @param book the book to add
     * @return true if added, false if at capacity
     */
    public boolean addBook(Book book) {
        if (books.size() >= MAX_BOOKS) {
            return false;
        }
        books.add(book);
        return true;
    }

    public boolean removeBook(String isbn) {
        return books.removeIf(b -> b.getIsbn().equals(isbn));
    }

    public Optional<Book> findByIsbn(String isbn) {
        for (Book b : books) {
            if (b.getIsbn().equals(isbn)) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    public List<Book> findByAuthor(String author) {
        List<Book> result = new ArrayList<>();
        for (Book book : books) {
            if (book.getAuthor().equalsIgnoreCase(author)) {
                result.add(book);
            }
        }
        return result;
    }

    public void checkout(String isbn) throws BookNotFoundException {
        Optional<Book> found = findByIsbn(isbn);
        if (!found.isPresent()) {
            throw new BookNotFoundException("No book with ISBN: " + isbn);
        }
        Book book = found.get();
        if (!book.isAvailable()) {
            throw new BookNotFoundException("Book already checked out: " + isbn);
        }
        book.setAvailable(false);
    }

    public int getTotalBooks() {
        return books.size();
    }

    public String getName() { return name; }
    public String getLocation() { return location; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }

    public static int getInstanceCount() { return instanceCount; }

    @Override
    public String toString() {
        return "Library[" + name + " @ " + location + ", books=" + books.size() + "]";
    }

    // ---- Inner Enum ----
    public enum Category {
        FICTION, NON_FICTION, SCIENCE, HISTORY, BIOGRAPHY
    }

    // ---- Inner Exception ----
    public static class BookNotFoundException extends Exception {
        public BookNotFoundException(String message) {
            super(message);
        }
    }

    // ---- Inner Class ----

    /**
     * Represents a book held by the library.
     */
    public static class Book {

        private String title;
        private String author;
        private String isbn;
        private int publicationYear;
        private boolean available;
        private Category category;

        public Book(String title, String author, String isbn, int publicationYear, Category category) {
            this.title = title;
            this.author = author;
            this.isbn = isbn;
            this.publicationYear = publicationYear;
            this.category = category;
            this.available = true;
        }

        // Getters
        public String getTitle() { return title; }
        public String getAuthor() { return author; }
        public String getIsbn() { return isbn; }
        public int getPublicationYear() { return publicationYear; }
        public boolean isAvailable() { return available; }
        public Category getCategory() { return category; }

        // Setters
        public void setAvailable(boolean available) { this.available = available; }

        @Override
        public String toString() {
            return String.format("[%s] \"%s\" by %s (%d) — %s",
                    isbn, title, author, publicationYear,
                    available ? "Available" : "Checked out");
        }
    }

    // ---- Inner Interface ----
    public interface Searchable {
        Optional<Book> findByIsbn(String isbn);
        List<Book> findByAuthor(String author);
    }
}
