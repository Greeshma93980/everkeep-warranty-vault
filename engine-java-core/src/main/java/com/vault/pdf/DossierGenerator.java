package com.vault.pdf;

import com.vault.rules.WarrantyCalculator;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

public final class DossierGenerator {
    private static final float PAGE_WIDTH = PDRectangle.LETTER.getWidth();
    private static final float LEFT = 40;
    private static final float TABLE_WIDTH = PAGE_WIDTH - LEFT * 2;
    private static final float FOOTER_BOTTOM = 24;
    private static final float FOOTER_HEIGHT = 28;
    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final Color INK = new Color(45, 58, 48);
    private static final Color GREEN = new Color(54, 111, 68);
    private static final Color BORDER = new Color(190, 201, 191);
    private static final Color PALE_GREEN = new Color(239, 246, 240);
    private static final float[] COLUMN_WIDTHS = {205, 105, 120, 102};
    private static final float ROW_HEIGHT = 24;

    private DossierGenerator() {}

    public static byte[] generate(
            String assetName,
            String serial,
            String hash,
            List<WarrantyCalculator.Result> coverages) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = newPage(document);
            drawPageHeader(document, page, assetName, serial, hash);

            float cursorY = drawCoverageHeading(document, page, 604);
            cursorY = drawCoverageColumnHeader(document, page, cursorY);

            for (WarrantyCalculator.Result coverage : coverages) {
                if (cursorY - ROW_HEIGHT < FOOTER_BOTTOM + FOOTER_HEIGHT + 12) {
                    page = newPage(document);
                    drawPageHeader(document, page, assetName, serial, hash);
                    cursorY = drawCoverageHeading(document, page, 665);
                    cursorY = drawCoverageColumnHeader(document, page, cursorY);
                }

                String[] values = {
                    coverage.name(),
                    coverage.status(),
                    coverage.expiryDate() == null ? "Not provided" : coverage.expiryDate().toString(),
                    Long.toString(coverage.remainingDays()),
                };
                drawCoverageRow(document, page, cursorY, values);
                cursorY -= ROW_HEIGHT;
            }

            for (PDPage documentPage : document.getPages()) {
                drawFooter(document, documentPage);
            }

            document.save(output);
            return output.toByteArray();
        }
    }

    private static PDPage newPage(PDDocument document) {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        return page;
    }

    private static void drawPageHeader(
            PDDocument document, PDPage page, String assetName, String serial, String hash)
            throws IOException {
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            drawBox(stream, LEFT, 716, TABLE_WIDTH, 40, PALE_GREEN);
            drawText(stream, BOLD, 17, LEFT + 12, 738, "Warranty Claim & Resale Dossier");
            drawText(stream, REGULAR, 8, LEFT + 12, 723, "Verified asset record and warranty summary");

            float tableTop = 701;
            float rowHeight = 22;
            String[][] details = {
                {"Asset", safe(assetName)},
                {"Serial number", safe(serial)},
                {"SHA-256", safe(hash)},
            };
            float labelWidth = 128;
            for (int index = 0; index < details.length; index++) {
                float rowBottom = tableTop - rowHeight * (index + 1);
                drawBox(stream, LEFT, rowBottom, TABLE_WIDTH, rowHeight, null);
                drawLine(stream, LEFT + labelWidth, rowBottom, LEFT + labelWidth, rowBottom + rowHeight);
                drawText(stream, BOLD, 9, LEFT + 8, rowBottom + 7, details[index][0]);
                drawText(stream, REGULAR, 9, LEFT + labelWidth + 8, rowBottom + 7,
                        fit(details[index][1], REGULAR, 9, TABLE_WIDTH - labelWidth - 16));
            }
        }
    }

    private static float drawCoverageHeading(PDDocument document, PDPage page, float y) throws IOException {
        try (PDPageContentStream stream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            drawText(stream, BOLD, 11, LEFT, y, "Component coverage:");
        }
        return y - 13;
    }

    private static float drawCoverageColumnHeader(PDDocument document, PDPage page, float topY)
            throws IOException {
        String[] labels = {"Component", "Status", "Expiry date", "Days remaining"};
        drawTableRow(document, page, topY, labels, true);
        return topY - ROW_HEIGHT;
    }

    private static void drawCoverageRow(
            PDDocument document, PDPage page, float topY, String[] values) throws IOException {
        drawTableRow(document, page, topY, values, false);
    }

    private static void drawTableRow(
            PDDocument document, PDPage page, float topY, String[] values, boolean header)
            throws IOException {
        try (PDPageContentStream stream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            float bottomY = topY - ROW_HEIGHT;
            drawBox(stream, LEFT, bottomY, TABLE_WIDTH, ROW_HEIGHT, header ? PALE_GREEN : null);

            float columnX = LEFT;
            for (int index = 0; index < COLUMN_WIDTHS.length; index++) {
                if (index > 0) {
                    drawLine(stream, columnX, bottomY, columnX, topY);
                }
                PDFont font = header ? BOLD : REGULAR;
                float fontSize = header ? 8 : 9;
                float maxWidth = COLUMN_WIDTHS[index] - 14;
                drawText(stream, font, fontSize, columnX + 7, bottomY + 8,
                        fit(safe(values[index]), font, fontSize, maxWidth));
                columnX += COLUMN_WIDTHS[index];
            }
        }
    }

    private static void drawFooter(PDDocument document, PDPage page) throws IOException {
        try (PDPageContentStream stream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            drawBox(stream, LEFT, FOOTER_BOTTOM, TABLE_WIDTH, FOOTER_HEIGHT, null);
            drawLine(stream, LEFT + 365, FOOTER_BOTTOM, LEFT + 365, FOOTER_BOTTOM + FOOTER_HEIGHT);
            drawText(stream, REGULAR, 8, LEFT + 8, FOOTER_BOTTOM + 10,
                    "Generated " + LocalDate.now());
            drawText(stream, BOLD, 8, LEFT + 376, FOOTER_BOTTOM + 10, "Everkeep | Warranty dossier");
        }
    }

    private static void drawBox(
            PDPageContentStream stream, float x, float y, float width, float height, Color fill)
            throws IOException {
        stream.setLineWidth(0.7f);
        stream.setStrokingColor(BORDER);
        if (fill != null) {
            stream.setNonStrokingColor(fill);
            stream.addRect(x, y, width, height);
            stream.fillAndStroke();
        } else {
            stream.addRect(x, y, width, height);
            stream.stroke();
        }
    }

    private static void drawLine(PDPageContentStream stream, float x1, float y1, float x2, float y2)
            throws IOException {
        stream.setLineWidth(0.7f);
        stream.setStrokingColor(BORDER);
        stream.moveTo(x1, y1);
        stream.lineTo(x2, y2);
        stream.stroke();
    }

    private static void drawText(
            PDPageContentStream stream, PDFont font, float size, float x, float y, String text)
            throws IOException {
        stream.beginText();
        stream.setNonStrokingColor(INK);
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(safe(text));
        stream.endText();
    }

    private static String fit(String value, PDFont font, float size, float maxWidth) throws IOException {
        String text = safe(value);
        if (font.getStringWidth(text) / 1000 * size <= maxWidth) {
            return text;
        }

        String suffix = "...";
        StringBuilder shortened = new StringBuilder();
        for (char character : text.toCharArray()) {
            String candidate = shortened.toString() + character + suffix;
            if (font.getStringWidth(candidate) / 1000 * size > maxWidth) {
                break;
            }
            shortened.append(character);
        }
        return shortened + suffix;
    }

    private static String safe(String value) {
        if (value == null) {
            return "Not provided";
        }
        return value
                .replace('\u2014', '-')
                .replace('\u2013', '-')
                .replace('\u2212', '-')
                .replaceAll("[^\\x20-\\x7E]", "-");
    }
}
