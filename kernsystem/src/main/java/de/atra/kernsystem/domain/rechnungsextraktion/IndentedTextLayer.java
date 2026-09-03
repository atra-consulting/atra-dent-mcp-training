package de.atra.kernsystem.domain.rechnungsextraktion;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.List;

class IndentedTextLayer extends PDFTextStripper {

    private boolean lineStart = true;

    IndentedTextLayer() {
        setSortByPosition(true);
        setPageEnd(getLineSeparator() + getLineSeparator());
    }

    @Override
    protected void startPage(PDPage page) throws IOException {
        lineStart = true;
        super.startPage(page);
    }

    @Override
    protected void writeLineSeparator() throws IOException {
        super.writeLineSeparator();
        lineStart = true;
    }

    @Override
    protected void writeParagraphSeparator() throws IOException {
        super.writeParagraphSeparator();
        lineStart = true;
    }

    @Override
    protected void writeString(String text, List<TextPosition> positions) throws IOException {
        if (lineStart) {
            lineStart = false;
            super.writeString(" ".repeat(indent(positions)));
        }
        super.writeString(text, positions);
    }

    private static int indent(List<TextPosition> positions) {
        if (positions.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.round(positions.getFirst().getXDirAdj()));
    }
}
