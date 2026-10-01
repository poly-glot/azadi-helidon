package guru.junaid.azadi.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository repository;

    @Test
    @DisplayName("Documents are listed with a file type taken from the extension")
    void listsDocumentsWithTheirType() {
        when(repository.findByCustomerId("CUST-1")).thenReturn(List.of(
            new Document("CUST-1", "Agreement", "agreement.PDF"), new Document("CUST-1", "Letter", "welcome.docx"), new Document("CUST-1", "Scan", "scan")));

        assertThat(new DocumentService(repository).documentsFor("CUST-1")).extracting(Document::fileType).containsExactly("pdf", "docx", "pdf");
    }
}
