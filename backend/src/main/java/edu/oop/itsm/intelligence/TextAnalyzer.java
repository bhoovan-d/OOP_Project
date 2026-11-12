package edu.oop.itsm.intelligence;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tiny, dependency-free NLP: tokenise -> drop stop-words -> map synonyms -> light stemming,
 * then compare documents with cosine similarity over term-frequency vectors.
 * Shared by duplicate detection, auto-categorisation and resolution suggestions.
 * ponytail: bag-of-words cosine; swap for embeddings (e.g. a sentence-transformer service) if recall matters.
 */
public final class TextAnalyzer {

    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "to", "of", "and", "or", "in", "on", "at", "for",
            "with", "my", "our", "we", "i", "me", "it", "its", "this", "that", "from", "by", "as", "please", "help",
            "issue", "problem", "since", "today", "any", "all", "some", "when", "while", "after", "again", "also",
            "have", "has", "had", "do", "does", "did", "make", "get", "getting", "users", "user", "us", "they", "them",
            "very", "just", "so", "but", "if", "there", "here", "am", "being", "about", "into", "out", "up", "can", "could");

    /** Different words people use for the same symptom collapse to one canonical token. */
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("unavailable", "outage"), Map.entry("down", "outage"), Map.entry("outage", "outage"),
            Map.entry("unreachable", "outage"), Map.entry("offline", "outage"), Map.entry("inaccessible", "outage"),
            Map.entry("unable", "fail"), Map.entry("cannot", "fail"), Map.entry("cant", "fail"), Map.entry("failing", "fail"),
            Map.entry("failed", "fail"), Map.entry("failure", "fail"), Map.entry("fails", "fail"), Map.entry("error", "fail"),
            Map.entry("errors", "fail"), Map.entry("broken", "fail"), Map.entry("not", "fail"), Map.entry("crash", "fail"),
            Map.entry("crashing", "fail"), Map.entry("crashed", "fail"),
            Map.entry("slow", "slow"), Map.entry("sluggish", "slow"), Map.entry("lag", "slow"), Map.entry("latency", "slow"),
            Map.entry("timeout", "slow"), Map.entry("timeouts", "slow"),
            Map.entry("payments", "payment"), Map.entry("pay", "payment"), Map.entry("checkout", "payment"),
            Map.entry("transaction", "payment"), Map.entry("transactions", "payment"),
            Map.entry("login", "login"), Map.entry("logon", "login"), Map.entry("signin", "login"), Map.entry("log", "login"),
            Map.entry("sign", "login"), Map.entry("authenticate", "login"), Map.entry("authentication", "login"),
            Map.entry("app", "application"), Map.entry("apps", "application"), Map.entry("portal", "application"),
            Map.entry("wifi", "wifi"), Map.entry("wireless", "wifi"),
            Map.entry("mail", "email"), Map.entry("e-mail", "email"), Map.entry("outlook", "email"),
            Map.entry("db", "database"), Map.entry("databases", "database"));

    private TextAnalyzer() {}

    public static Map<String, Integer> termFrequencies(String text) {
        Map<String, Integer> tf = new HashMap<>();
        if (text == null) return tf;
        for (String raw : text.toLowerCase().replace("'", "").split("[^a-z0-9]+")) {
            String token = normalize(raw);
            if (token != null) tf.merge(token, 1, Integer::sum);
        }
        return tf;
    }

    /** Symptoms ("down", "error", "slow") are shared by unrelated incidents, so they weigh half as much as the subject. */
    private static final Set<String> SYMPTOMS = Set.of("fail", "outage", "slow", "work");

    /** Title words count double: titles are short and dense with meaning. */
    public static Map<String, Integer> incidentVector(String title, String description) {
        Map<String, Integer> v = termFrequencies(description);
        termFrequencies(title).forEach((k, n) -> v.merge(k, n * 2, Integer::sum));
        v.replaceAll((k, n) -> SYMPTOMS.contains(k) ? n : n * 2);
        return v;
    }

    static String normalize(String raw) {
        if (raw.length() < 2 || STOP.contains(raw)) return null;
        String syn = SYNONYMS.get(raw);
        if (syn != null) return syn;
        String s = stem(raw);
        return SYNONYMS.getOrDefault(s, s);
    }

    /** Light suffix stripping (good enough for incident jargon; Porter would be overkill). */
    static String stem(String w) {
        if (w.length() > 5 && w.endsWith("ing")) return w.substring(0, w.length() - 3);
        if (w.length() > 4 && w.endsWith("ed")) return w.substring(0, w.length() - 2);
        if (w.length() > 4 && w.endsWith("es") && !w.endsWith("ses")) return w.substring(0, w.length() - 2);
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) return w.substring(0, w.length() - 1);
        return w;
    }

    public static double cosine(Map<String, Integer> a, Map<String, Integer> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Map<String, Integer> small = a.size() <= b.size() ? a : b;
        Map<String, Integer> large = small == a ? b : a;
        double dot = 0;
        for (var e : small.entrySet()) {
            Integer o = large.get(e.getKey());
            if (o != null) dot += e.getValue() * o;
        }
        if (dot == 0) return 0;
        return dot / (norm(a) * norm(b));
    }

    private static double norm(Map<String, Integer> v) {
        double s = 0;
        for (int x : v.values()) s += (double) x * x;
        return Math.sqrt(s);
    }
}
