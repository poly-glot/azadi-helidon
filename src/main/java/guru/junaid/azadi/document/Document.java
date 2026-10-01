package guru.junaid.azadi.document;

import java.util.Locale;

public record Document(String customerId, String title, String fileName) {

    public String fileType() {
        var dot = fileName.lastIndexOf('.');
        return dot < 0 ? "pdf" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public String getTitle() {
        return title;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileType() {
        return fileType();
    }
}
