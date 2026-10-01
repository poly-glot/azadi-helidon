package guru.junaid.azadi.common;

import com.google.cloud.NoCredentials;
import com.google.cloud.Timestamp;
import com.google.cloud.datastore.BaseEntity;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.EntityQuery;
import com.google.cloud.datastore.FullEntity;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import io.helidon.config.Config;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class Db {

    public final class Select<T extends Record> {

        private final Records<T> rows;
        private final EntityQuery.Builder query;

        private Select(Records<T> rows) {
            this.rows = rows;
            this.query = Query.newEntityQueryBuilder().setKind(rows.kind());
        }

        public Select<T> where(String property, String value) {
            query.setFilter(PropertyFilter.eq(property, value));
            return this;
        }

        public Optional<T> first() {
            var found = ds.run(query.setLimit(1).build());
            return found.hasNext() ? Optional.of(rows.from(found.next())) : Optional.empty();
        }

        public List<T> list() {
            var all = new ArrayList<T>();
            ds.run(query.build()).forEachRemaining(entity -> all.add(rows.from(entity)));
            return all;
        }
    }

    private final Datastore ds;

    public Db(Config config) {
        var host = config.get("datastore.host").asString().orElse("");
        var options = DatastoreOptions.newBuilder()
            .setProjectId(config.get("gcp.project.id").asString().get())
            .setDatabaseId(config.get("firestore.db").asString().get());

        if (!host.isEmpty()) {
            options.setHost(host.contains("://") ? host : "http://" + host);
        }
        if (!config.get("use.adc").asBoolean().orElse(false)) {
            options.setCredentials(NoCredentials.getInstance());
        }
        ds = options.build().getService();
    }

    public <T extends Record> Select<T> query(Records<T> rows) {
        return new Select<>(rows);
    }

    public <T extends Record> Optional<T> find(Records<T> rows, long id) {
        return Optional.ofNullable(ds.get(ds.newKeyFactory().setKind(rows.kind()).newKey(id))).map(rows::from);
    }

    public <T extends Record> T save(Records<T> rows, T record) {
        var id = rows.id(record);
        if (id == 0) {
            return rows.from(add(rows.kind(), builder -> rows.fill(builder, record)));
        }
        var builder = Entity.newBuilder(ds.newKeyFactory().setKind(rows.kind()).newKey(id));
        rows.fill(builder, record);
        return rows.from(ds.put(builder.build()));
    }

    public boolean isEmpty(String kind) {
        return !ds.run(Query.newEntityQueryBuilder().setKind(kind).setLimit(1).build()).hasNext();
    }

    public Entity add(String kind, Consumer<BaseEntity.Builder<?, ?>> fill) {
        var builder = FullEntity.newBuilder(ds.newKeyFactory().setKind(kind).newKey());
        fill.accept(builder);
        return ds.add(builder.build());
    }

    public static LocalDate date(Entity e, String property) {
        return e.contains(property) && !e.isNull(property)
            ? LocalDate.ofInstant(Instant.ofEpochSecond(e.getTimestamp(property).getSeconds()), ZoneOffset.UTC) : null;
    }

    public static Timestamp at(LocalDate date) {
        return at(date.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    public static Timestamp at(Instant instant) {
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }
}
