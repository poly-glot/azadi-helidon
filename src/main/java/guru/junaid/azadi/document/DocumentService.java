package guru.junaid.azadi.document;

import java.util.List;

public final class DocumentService {

    private final DocumentRepository documents;

    public DocumentService(DocumentRepository documents) {
        this.documents = documents;
    }

    public List<Document> documentsFor(String customerId) {
        return documents.findByCustomerId(customerId);
    }
}
