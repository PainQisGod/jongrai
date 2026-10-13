package jongrai;

/** When a hero is strongest. ALL = strong throughout (no single power spike). */
public enum Spike {
    EARLY, MID, LATE, ALL;

    public static Spike parse(String text) {
        try {
            return Spike.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown spike '" + text.trim() + "'");
        }
    }
}
