package jongrai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The one CSV reader every data file goes through.
 *
 * A field containing a comma or a quote is wrapped in double quotes, with internal quotes doubled.
 * Lists inside one field use ";". Blank lines and lines starting with "#" are skipped, a UTF-8 BOM
 * is ignored, and the first remaining line is the column-name row. Any malformed row fails the
 * whole load with "file:line: reason", so bad data never slips through silently.
 */
public final class Csv {

    private Csv() {}

    /** One data row, remembering where it came from for error messages. */
    public record Row(Path file, int line, List<String> cells) {

        public String text(int i) {
            return i < cells.size() ? cells.get(i).trim() : "";
        }

        /** A whole integer, optionally followed by a unit word ("11060 gold"). Blank gives ifBlank. */
        public int integer(int i, int ifBlank) throws IOException {
            String t = text(i);
            if (t.isEmpty()) return ifBlank;
            String digits = t.replaceFirst("(?i)\\s*(gold)$", "");
            try {
                return Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                throw fail("column " + (i + 1) + " is not a whole number: '" + t + "'");
            }
        }

        public double decimal(int i, double ifBlank) throws IOException {
            String t = text(i);
            if (t.isEmpty()) return ifBlank;
            try {
                return Double.parseDouble(t);
            } catch (NumberFormatException e) {
                throw fail("column " + (i + 1) + " is not a number: '" + t + "'");
            }
        }

        /** A ";"-separated list field as trimmed, non-empty, unmodifiable entries. */
        public List<String> list(int i) {
            String t = text(i);
            if (t.isEmpty()) return List.of();
            return Arrays.stream(t.split(";")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }

        public IOException fail(String reason) {
            return new IOException(file + ":" + line + ": " + reason);
        }
    }

    /**
     * Reads a data file. Every row must have at least {@code required} fields and at most as many
     * as the header row (so an unquoted comma inside a text field is caught, not truncated).
     */
    public static List<Row> read(Path file, int required) throws IOException {
        List<String> lines = Files.readAllLines(file);
        List<Row> rows = new ArrayList<>();
        int width = -1;
        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n);
            if (n == 0 && line.startsWith("﻿")) line = line.substring(1);
            if (line.isBlank() || line.startsWith("#")) continue;
            List<String> cells = split(line);
            if (width < 0) { width = cells.size(); continue; }   // the column-name row
            Row row = new Row(file, n + 1, List.copyOf(cells));
            if (cells.size() < required) throw row.fail("expected at least " + required + " fields, found " + cells.size());
            if (cells.size() > width) throw row.fail("expected at most " + width + " fields, found " + cells.size()
                    + " (quote any field that contains a comma)");
            rows.add(row);
        }
        return rows;
    }

    static List<String> split(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c != '"') cell.append(c);
                else if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else quoted = false;
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }
}
