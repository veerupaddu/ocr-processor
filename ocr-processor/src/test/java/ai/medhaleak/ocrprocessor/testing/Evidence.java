package ai.medhaleak.ocrprocessor.testing;

/** One concrete fact from the case that just ran, shown in the Tests tab. */
public final class Evidence {

    private static final ThreadLocal<String> NOTE = new ThreadLocal<>();

    private Evidence() {
    }

    /** Records what the case did and what it checked, in words a person can read. */
    public static void record(String executed, String validated) {
        saw("Executed: " + clip(executed) + ". Validated: " + clip(validated) + ".");
    }

    public static void record(String executed, String validated, String observed) {
        record(executed, validated + ". Observed: " + observed);
    }

    public static void saw(String fact) {
        String existing = NOTE.get();
        String next = existing == null || existing.isBlank() ? fact : existing + " | " + fact;
        NOTE.set(next);
        System.out.println("EVIDENCE: " + fact);
    }

    private static String clip(String value) {
        if (value == null) {
            return "(none)";
        }
        String collapsed = value.replaceAll("\\s+", " ").trim();
        if (collapsed.isEmpty()) {
            return "(empty)";
        }
        return collapsed.length() > 320 ? collapsed.substring(0, 320) + "…" : collapsed;
    }

    public static void clear() {
        NOTE.remove();
    }

    static String take() {
        String value = NOTE.get();
        NOTE.remove();
        return value == null ? "" : value;
    }
}
