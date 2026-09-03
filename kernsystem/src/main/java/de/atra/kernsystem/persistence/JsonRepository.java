package de.atra.kernsystem.persistence;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.type.CollectionType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

public class JsonRepository<T> {

    private final Path file;
    private final Class<T> type;
    private final ToLongFunction<T> idReader;
    private final ObjectMapper mapper;
    private final CollectionType listType;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final long firstId;

    private List<T> entries;
    private long nextId;

    public JsonRepository(Path file, Class<T> type, ToLongFunction<T> idReader,
                          long firstId, ObjectMapper mapper) {
        this.file = file;
        this.type = type;
        this.idReader = idReader;
        this.firstId = firstId;
        this.mapper = mapper.rebuild().enable(SerializationFeature.INDENT_OUTPUT).build();
        this.listType = this.mapper.getTypeFactory().constructCollectionType(List.class, type);
        load(firstId);
    }

    private void load(long firstId) {
        checkDirectory();
        try {
            if (Files.notExists(file)) {
                this.entries = new ArrayList<>();
                write();
            } else {
                this.entries = new ArrayList<>(mapper.readValue(Files.readString(file), listType));
            }
        } catch (Exception cause) {
            throw new IllegalStateException(explain(file, cause), cause);
        }
        this.nextId = entries.stream().mapToLong(idReader).max().orElse(firstId - 1) + 1;
    }

    private static String explain(Path file, Exception cause) {
        String message = "Repository " + file + " could not be loaded";
        if (cause instanceof tools.jackson.databind.DatabindException) {
            return message + ". The file holds a value this build no longer reads, so it is "
                    + "older than the code. It is a working copy of kernsystem/data and can be "
                    + "discarded: ./start.sh --bestand-zuruecksetzen";
        }
        return message;
    }

    private void checkDirectory() {
        Path directory = file.toAbsolutePath().getParent();
        if (Files.isDirectory(directory)) {
            return;
        }
        throw new IllegalStateException("""
                Data directory %s does not exist.
                Working directory is %s.
                The application expects the directory from kernsystem.data-directory \
                relative to it -- so it has to be started from the module directory \
                kernsystem. In IntelliJ that is the working directory of the run \
                configuration; alternatively set kernsystem.data-directory to an \
                absolute path."""
                .formatted(directory, Path.of("").toAbsolutePath()));
    }

    public void reload() {
        lock.writeLock().lock();
        try {
            load(firstId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<T> all() {
        lock.readLock().lock();
        try {
            return entries.stream().map(this::copy).toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<T> find(long id) {
        lock.readLock().lock();
        try {
            return entries.stream().filter(e -> idReader.applyAsLong(e) == id)
                    .map(this::copy).findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    private T copy(T original) {
        try {
            String json = mapper.writeValueAsString(original);
            return mapper.readValue(json, type);
        } catch (Exception cause) {
            throw new IllegalStateException("Copy could not be created", cause);
        }
    }

    public T add(LongFunction<T> buildWith) {
        lock.writeLock().lock();
        try {
            T entry = buildWith.apply(nextId);
            entries.add(entry);
            nextId++;
            write();
            return copy(entry);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public T replace(long id, T entry) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < entries.size(); i++) {
                if (idReader.applyAsLong(entries.get(i)) == id) {
                    entries.set(i, entry);
                    write();
                    return copy(entry);
                }
            }
            throw new NoSuchElementException("No entry with the ID " + id + " in " + file);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public T update(long id, UnaryOperator<T> change) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < entries.size(); i++) {
                if (idReader.applyAsLong(entries.get(i)) == id) {
                    T changed = change.apply(copy(entries.get(i)));
                    entries.set(i, changed);
                    write();
                    return copy(changed);
                }
            }
            throw new NoSuchElementException("No entry with id " + id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void write() {
        Path temp = null;
        try {
            temp = Files.createTempFile(file.toAbsolutePath().getParent(), "repository", ".tmp");
            String json = mapper.writeValueAsString(entries) + "\n";
            Files.writeString(temp, json);
            Files.move(temp, file,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception cause) {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (Exception ignored) {
                }
            }
            throw new IllegalStateException("Repository " + file + " could not be written", cause);
        }
    }
}
