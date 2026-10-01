package guru.junaid.azadi.document;

import guru.junaid.azadi.common.Db;
import guru.junaid.azadi.common.Records;

import java.util.List;

public final class DocumentRepository {

    private static final Records<Document> ROWS = Records.of(Document.class, "Document");

    private final Db db;

    public DocumentRepository(Db db) {
        this.db = db;
    }

    public List<Document> findByCustomerId(String customerId) {
        return db.query(ROWS).where("customerId", customerId).list();
    }
}
