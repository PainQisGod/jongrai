package jongrai;

public enum Lane {
    EXP, JUNGLE, MID, GOLD, ROAM;

    public static Lane parse(String text) {
        try {
            return Lane.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown lane '" + text.trim() + "'");
        }
    }
}
