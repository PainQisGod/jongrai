package jongrai;

public enum Lane {
    EXP, JUNGLE, MID, GOLD, ROAM;

    public static Lane parse(String text) {
        return Lane.valueOf(text.trim().toUpperCase());
    }
}
