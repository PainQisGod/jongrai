package jongrai;

public enum Role {
    TANK, FIGHTER, ASSASSIN, MAGE, MARKSMAN, SUPPORT;

    public static Role parse(String text) {
        try {
            return Role.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown role '" + text.trim() + "'");
        }
    }
}
