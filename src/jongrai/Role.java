package jongrai;

public enum Role {
    TANK, FIGHTER, ASSASSIN, MAGE, MARKSMAN, SUPPORT;

    public static Role parse(String text) {
        return Role.valueOf(text.trim().toUpperCase());
    }
}
