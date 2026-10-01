package guru.junaid.azadi.common;

import com.google.cloud.datastore.BaseEntity;
import com.google.cloud.datastore.Entity;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class Records<T extends Record> {

    private static final String ID = "id";

    @FunctionalInterface
    private interface Reader {
        Object read(Entity e, String property);
    }

    @FunctionalInterface
    private interface Writer {
        void write(BaseEntity.Builder<?, ?> builder, String property, Object value);
    }

    private record Column(String name, boolean key, RecordComponent component, Reader reader, Writer writer) { }

    private static final Map<Class<?>, Reader> READERS = Map.of(
        String.class, (e, p) -> has(e, p) ? e.getString(p) : null,
        long.class, Records::number,
        int.class, (e, p) -> (int) number(e, p),
        boolean.class, (e, p) -> has(e, p) && e.getBoolean(p),
        LocalDate.class, (e, p) -> has(e, p) ? LocalDate.ofInstant(instant(e, p), ZoneOffset.UTC) : null,
        Instant.class, (e, p) -> has(e, p) ? instant(e, p) : null);

    private static final Map<Class<?>, Writer> WRITERS = Map.of(
        String.class, (b, p, v) -> set(b, p, (String) v),
        long.class, (b, p, v) -> b.set(p, (long) v),
        int.class, (b, p, v) -> b.set(p, (long) (int) v),
        boolean.class, (b, p, v) -> b.set(p, (boolean) v),
        LocalDate.class, (b, p, v) -> set(b, p, (LocalDate) v),
        Instant.class, (b, p, v) -> set(b, p, (Instant) v));

    private final String kind;
    private final Constructor<T> constructor;
    private final List<Column> columns;

    private Records(String kind, Constructor<T> constructor, List<Column> columns) {
        this.kind = kind;
        this.constructor = constructor;
        this.columns = columns;
    }

    public static <T extends Record> Records<T> of(Class<T> type, String kind) {
        var components = type.getRecordComponents();
        var types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        try {
            return new Records<>(kind, type.getDeclaredConstructor(types), Arrays.stream(components).map(Records::column).toList());
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(type + " has no canonical constructor", e);
        }
    }

    private static Column column(RecordComponent component) {
        var type = component.getType();
        var key = ID.equals(component.getName()) && type == long.class;
        if (!key && !READERS.containsKey(type)) {
            throw new IllegalArgumentException("Unsupported record component type " + type + " for " + component.getName());
        }
        return new Column(component.getName(), key, component, READERS.get(type), WRITERS.get(type));
    }

    public String kind() {
        return kind;
    }

    public T from(Entity entity) {
        var values = columns.stream()
            .map(column -> column.key() ? entity.getKey().getId() : column.reader().read(entity, column.name()))
            .toArray();
        try {
            return constructor.newInstance(values);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Could not map " + kind + " entity " + entity.getKey(), e);
        }
    }

    public long id(T record) {
        return columns.stream().filter(Column::key).findFirst().map(column -> (Long) value(record, column)).orElse(0L);
    }

    public void fill(BaseEntity.Builder<?, ?> builder, T record) {
        columns.stream().filter(column -> !column.key())
            .forEach(column -> column.writer().write(builder, column.name(), value(record, column)));
    }

    private Object value(T record, Column column) {
        try {
            return column.component().getAccessor().invoke(record);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Could not read " + column.name() + " of " + kind, e);
        }
    }

    private static boolean has(Entity e, String property) {
        return e.contains(property) && !e.isNull(property);
    }

    private static long number(Entity e, String property) {
        return has(e, property) ? e.getLong(property) : 0L;
    }

    private static Instant instant(Entity e, String property) {
        var stamp = e.getTimestamp(property);
        return Instant.ofEpochSecond(stamp.getSeconds(), stamp.getNanos());
    }

    private static void set(BaseEntity.Builder<?, ?> builder, String property, String value) {
        if (value == null) {
            builder.setNull(property);
        } else {
            builder.set(property, value);
        }
    }

    private static void set(BaseEntity.Builder<?, ?> builder, String property, LocalDate value) {
        if (value == null) {
            builder.setNull(property);
        } else {
            builder.set(property, Db.at(value));
        }
    }

    private static void set(BaseEntity.Builder<?, ?> builder, String property, Instant value) {
        if (value == null) {
            builder.setNull(property);
        } else {
            builder.set(property, Db.at(value));
        }
    }
}
