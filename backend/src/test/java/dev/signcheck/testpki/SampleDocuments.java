package dev.signcheck.testpki;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Unsigned documents used as input for {@link SignedSamples}. Every document contains
 * {@link #CONTENT_MARKER} as plain, uncompressed bytes so {@link SignedSamples#tamper(byte[])} can
 * change the signed content without breaking the file structure.
 */
public final class SampleDocuments {

    /** Text present, uncompressed, in every sample document. */
    public static final String CONTENT_MARKER = "SignCheck sample content";

    private SampleDocuments() {
    }

    /** A one-page PDF whose page content stream is stored without compression. */
    public static byte[] pdf() {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            // compress = false keeps CONTENT_MARKER readable in the file bytes
            try (PDPageContentStream content = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.OVERWRITE, false)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(CONTENT_MARKER + " - TEST document, not a real contract");
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A small XML document (input for XAdES enveloped and enveloping signatures). */
    public static byte[] xml() {
        return ("""
                <?xml version="1.0" encoding="UTF-8"?>
                <document id="sample">
                  <title>TEST document, not a real contract</title>
                  <content>%s</content>
                </document>
                """.formatted(CONTENT_MARKER)).getBytes(StandardCharsets.UTF_8);
    }

    /** A plain text document (input for detached signatures and CAdES). */
    public static byte[] text() {
        return (CONTENT_MARKER + "\nTEST document, not a real contract\n").getBytes(StandardCharsets.UTF_8);
    }
}
