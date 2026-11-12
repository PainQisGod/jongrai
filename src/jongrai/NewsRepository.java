package jongrai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * News articles, one text file each in data/news/ (see data/news/_TEMPLATE.txt for the format).
 * Files are read again whenever one is added, changed or removed, so a new article goes live without a restart.
 * Files whose name starts with "_" are ignored, which keeps the template and drafts off the site.
 */
public class NewsRepository {

    public static final Set<String> TYPES = Set.of("patch", "skin", "event", "esports", "news");

    /** One article. bodyKm and the *Km fields may be empty: the page falls back to English. */
    public record Article(String slug, String title, String titleKm, String type, LocalDate date, LocalDate ends,
                          List<String> heroes, String image, String summary, String summaryKm, String author,
                          String body, String bodyKm) {}

    private final Path dir;
    private String stamp = "";
    private List<Article> articles = List.of();
    private List<String> problems = List.of();

    public NewsRepository(Path dir) { this.dir = dir; }

    /** Newest first. Re-reads the folder if anything in it changed since the last call. */
    public synchronized List<Article> all() {
        try {
            String now = stampOf();
            if (!now.equals(stamp)) { load(); stamp = now; }
        } catch (IOException e) {
            problems = List.of("news: cannot read " + dir + ": " + e.getMessage());
        }
        return articles;
    }

    public Article find(String slug) {
        return all().stream().filter(a -> a.slug().equals(slug)).findFirst().orElse(null);
    }

    /** Problems found in the files on the last read (bad date, unknown type, ...); each such file is skipped. */
    public synchronized List<String> problems() { all(); return problems; }

    private String stampOf() throws IOException {
        if (!Files.isDirectory(dir)) return "none";
        StringBuilder sb = new StringBuilder();
        try (var files = Files.list(dir)) {
            for (Path p : files.filter(NewsRepository::isArticle).sorted().toList())
                sb.append(p.getFileName()).append(Files.getLastModifiedTime(p).toMillis()).append(Files.size(p)).append(';');
        }
        return sb.toString();
    }

    private static boolean isArticle(Path p) {
        String n = p.getFileName().toString();
        return n.toLowerCase(Locale.ROOT).endsWith(".txt") && !n.startsWith("_") && Files.isRegularFile(p);
    }

    private void load() throws IOException {
        List<Article> out = new ArrayList<>();
        List<String> bad = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (var files = Files.list(dir)) {
                for (Path p : files.filter(NewsRepository::isArticle).sorted().toList()) {
                    try { out.add(parse(p)); }
                    catch (IllegalArgumentException e) { bad.add("data/news/" + p.getFileName() + ": " + e.getMessage() + " (article skipped)"); }
                }
            }
        }
        out.sort(Comparator.comparing(Article::date).reversed().thenComparing(Article::slug));
        articles = List.copyOf(out);
        problems = List.copyOf(bad);
        bad.forEach(b -> System.out.println("warning: " + b));
    }

    /** Header lines "key: value" until the first blank line, then the English body, then an optional "---km" Khmer body. */
    static Article parse(Path file) throws IOException {
        String name = file.getFileName().toString();
        String slug = Hero.slugOf(name.substring(0, name.length() - 4));
        String text = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n");
        if (text.startsWith("﻿")) text = text.substring(1);
        Map<String, String> head = new LinkedHashMap<>();
        String[] lines = text.split("\n", -1);
        int i = 0;
        for (; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) { if (head.isEmpty()) continue; break; }  // blank lines before the first field don't end the header
            if (line.startsWith("#")) continue;
            int c = line.indexOf(':');
            if (c < 1) throw new IllegalArgumentException("line " + (i + 1) + " should be 'field: value' (or leave a blank line before the story)");
            head.put(line.substring(0, c).strip().toLowerCase(Locale.ROOT), line.substring(c + 1).strip());
        }
        String rest = String.join("\n", java.util.Arrays.copyOfRange(lines, Math.min(i + 1, lines.length), lines.length));
        String body = rest, bodyKm = "";
        java.util.regex.Matcher km = java.util.regex.Pattern.compile("(?m)^---\\s*km\\s*$").matcher(rest);
        if (km.find()) { body = rest.substring(0, km.start()); bodyKm = rest.substring(km.end()); }

        String title = head.getOrDefault("title", "");
        if (title.isEmpty()) throw new IllegalArgumentException("missing 'title:'");
        String type = head.getOrDefault("type", "news").toLowerCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw new IllegalArgumentException("type must be one of " + new java.util.TreeSet<>(TYPES) + ", found '" + type + "'");
        LocalDate date = date(head.get("date"), "date", true);
        LocalDate ends = date(head.get("ends"), "ends", false);
        List<String> heroes = new ArrayList<>();
        for (String h : head.getOrDefault("heroes", "").split(";")) if (!h.isBlank()) heroes.add(h.strip());
        return new Article(slug, title, head.getOrDefault("title_km", ""), type, date, ends, List.copyOf(heroes),
                head.getOrDefault("image", ""), head.getOrDefault("summary", ""), head.getOrDefault("summary_km", ""),
                head.getOrDefault("author", "PainQ"), body.strip(), bodyKm.strip());
    }

    private static LocalDate date(String v, String field, boolean required) {
        if (v == null || v.isBlank()) {
            if (required) throw new IllegalArgumentException("missing '" + field + ":' (write it as 2026-11-12)");
            return null;
        }
        try { return LocalDate.parse(v.strip()); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("'" + field + ": " + v + "' must be a date like 2026-11-12"); }
    }

    /** Images named in articles (cover and [image: ...] lines) that are not in web/news/, plus heroes nobody knows. */
    public List<String> validate(Set<String> heroKeys, Path imageDir) {
        List<String> out = new ArrayList<>(problems());
        java.util.regex.Pattern img = java.util.regex.Pattern.compile("(?m)^\\[image:\\s*([^|\\]]+)");
        for (Article a : all()) {
            List<String> files = new ArrayList<>();
            if (!a.image().isEmpty()) files.add(a.image());
            for (String b : List.of(a.body(), a.bodyKm())) { var m = img.matcher(b); while (m.find()) files.add(m.group(1).strip()); }
            for (String f : files)
                if (!Files.isRegularFile(imageDir.resolve(f))) out.add("news " + a.slug() + ": image '" + f + "' is not in web/news/");
            for (String h : a.heroes())
                if (!heroKeys.contains(h.toLowerCase(Locale.ROOT))) out.add("news " + a.slug() + ": unknown hero '" + h + "'");
        }
        return out;
    }
}
