package it.lo.exp.nulladies;

public enum TaskColor {
    RED, ORANGE, YELLOW, GREEN, TEAL, BLUE, PURPLE, GRAY;

    public int toArgb() {
        switch (this) {
            case RED:    return 0xFFE53935;
            case ORANGE: return 0xFFFB8C00;
            case YELLOW: return 0xFFFDD835;
            case GREEN:  return 0xFF43A047;
            case TEAL:   return 0xFF00897B;
            case BLUE:   return 0xFF1E88E5;
            case PURPLE: return 0xFF8E24AA;
            case GRAY:   return 0xFF757575;
            default:     return 0xFF757575;
        }
    }

    public String displayName() {
        switch (this) {
            case RED:    return "Red";
            case ORANGE: return "Orange";
            case YELLOW: return "Yellow";
            case GREEN:  return "Green";
            case TEAL:   return "Teal";
            case BLUE:   return "Blue";
            case PURPLE: return "Purple";
            case GRAY:   return "Gray";
            default:     return "Gray";
        }
    }

    public static TaskColor fromName(String name) {
        if (name == null) return BLUE;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BLUE;
        }
    }
}
