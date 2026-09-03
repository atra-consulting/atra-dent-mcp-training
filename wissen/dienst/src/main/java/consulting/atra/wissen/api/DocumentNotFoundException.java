package consulting.atra.wissen.api;

import java.util.List;

class DocumentNotFoundException extends RuntimeException {

    private final String documentId;

    DocumentNotFoundException(String dokumentId, List<String> bekannteIds) {
        super("Ein Dokument mit der Id '" + dokumentId + "' gibt es nicht. Bekannt sind "
                + String.join(", ", bekannteIds));
        this.documentId = dokumentId;
    }

    String documentId() {
        return documentId;
    }
}
