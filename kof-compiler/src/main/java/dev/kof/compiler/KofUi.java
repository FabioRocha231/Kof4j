package dev.kof.compiler;

import java.util.List;

/**
 * KofUi — builtin registry for the kof.ui standard library.
 *
 * Foundation for the UI platform: Color (32-bit RGBA packed in an Int),
 * Palette (named colors) and Theme (light/dark semantic colors).
 *
 * A Color value is a packed Int: 0xAARRGGBB (alpha in the low byte, the
 * opposite of web convention — Kof is big-endian here: 0xRRGGBBAA).
 * Actually the layout is: (r << 24) | (g << 16) | (b << 8) | a.
 * All channel access is compiler-side bit manipulation; only toCss() needs
 * a runtime helper (string building).
 */
final class KofUi {

    private KofUi() {}

    static final Type COLOR = new Type.ClassType("kof.ui", "Color", List.of());
    static final Type THEME = new Type.ClassType("kof.ui", "Theme", List.of());

    static boolean isColor(Type t) { return COLOR.equals(t); }
    static boolean isTheme(Type t) { return THEME.equals(t); }

    static boolean isUiType(Type t) {
        return isColor(t) || isTheme(t);
    }

    static boolean isConstructor(String name) {
        return "Color".equals(name) || "Theme".equals(name);
    }

    static Type constructorType(String name) {
        return "Color".equals(name) ? COLOR : Type.UnknownType.UNKNOWN;
    }

    static boolean isPalette(String name) {
        return "Palette".equals(name);
    }

    record UiCall(String function, Type returnType, List<Type> parameterTypes) {}

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type BOOL = Type.PrimitiveType.BOOL;

    static UiCall staticMethod(String className, String name, int argCount) {
        if ("Color".equals(className)) {
            return switch (name) {
                case "rgba" -> argCount == 4
                        ? new UiCall("kof_ui_color_rgba", COLOR, List.of(INT, INT, INT, INT)) : null;
                default -> null;
            };
        }
        if ("Theme".equals(className)) {
            return switch (name) {
                case "light" -> argCount == 0 ? new UiCall("kof_ui_theme_light", THEME, List.of()) : null;
                case "dark" -> argCount == 0 ? new UiCall("kof_ui_theme_dark", THEME, List.of()) : null;
                default -> null;
            };
        }
        return null;
    }

    static UiCall instanceMethod(Type receiver, String name, int argCount) {
        if (isColor(receiver)) {
            return switch (name) {
                case "red" -> argCount == 0 ? new UiCall("kof_ui_color_red", INT, List.of()) : null;
                case "green" -> argCount == 0 ? new UiCall("kof_ui_color_green", INT, List.of()) : null;
                case "blue" -> argCount == 0 ? new UiCall("kof_ui_color_blue", INT, List.of()) : null;
                case "alpha" -> argCount == 0 ? new UiCall("kof_ui_color_alpha", INT, List.of()) : null;
                case "toCss" -> argCount == 0 ? new UiCall("kof_ui_color_to_css", STR, List.of()) : null;
                case "withAlpha" -> argCount == 1 ? new UiCall("kof_ui_color_with_alpha", COLOR, List.of(INT)) : null;
                case "isOpaque" -> argCount == 0 ? new UiCall("kof_ui_color_is_opaque", BOOL, List.of()) : null;
                default -> null;
            };
        }
        if (isTheme(receiver)) {
            return switch (name) {
                case "background" -> argCount == 0 ? new UiCall("kof_ui_theme_background", COLOR, List.of()) : null;
                case "surface" -> argCount == 0 ? new UiCall("kof_ui_theme_surface", COLOR, List.of()) : null;
                case "primary" -> argCount == 0 ? new UiCall("kof_ui_theme_primary", COLOR, List.of()) : null;
                case "secondary" -> argCount == 0 ? new UiCall("kof_ui_theme_secondary", COLOR, List.of()) : null;
                case "text" -> argCount == 0 ? new UiCall("kof_ui_theme_text", COLOR, List.of()) : null;
                case "error" -> argCount == 0 ? new UiCall("kof_ui_theme_error", COLOR, List.of()) : null;
                case "isDark" -> argCount == 0 ? new UiCall("kof_ui_theme_is_dark", BOOL, List.of()) : null;
                default -> null;
            };
        }
        return null;
    }

    /** Palette.<name> — packed 0xRRGGBBAA color constants. */
    static Integer paletteColor(String name) {
        return switch (name) {
            case "red" -> 0xFF0000FF;
            case "green" -> 0x00FF00FF;
            case "blue" -> 0x0000FFFF;
            case "yellow" -> 0xFFFF00FF;
            case "cyan" -> 0x00FFFFFF;
            case "magenta" -> 0xFF00FFFF;
            case "black" -> 0x000000FF;
            case "white" -> 0xFFFFFFFF;
            case "gray", "grey" -> 0x808080FF;
            case "transparent" -> 0x00000000;
            case "orange" -> 0xFF8000FF;
            case "purple" -> 0x800080FF;
            case "pink" -> 0xFFC0CBFF;
            case "brown" -> 0xA52A2AFF;
            default -> null;
        };
    }

    /** Semantic colors per theme tag (0 = light, 1 = dark). */
    static Integer themeColor(String role, int tag) {
        if (tag == 1) {
            return switch (role) {
                case "background" -> 0x121212FF;
                case "surface" -> 0x1E1E1EFF;
                case "primary" -> 0xBB86FCFF;
                case "secondary" -> 0x03DAC6FF;
                case "text" -> 0xFFFFFFFF;
                case "error" -> 0xCF6679FF;
                default -> null;
            };
        }
        return switch (role) {
            case "background" -> 0xFFFFFFFF;
            case "surface" -> 0xF2F2F2FF;
            case "primary" -> 0x6200EEFF;
            case "secondary" -> 0x03DAC6FF;
            case "text" -> 0x000000FF;
            case "error" -> 0xB00020FF;
            default -> null;
        };
    }
}