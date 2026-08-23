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
        return "Color".equals(name);
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
            case "green" -> 0xFF00FF00;
            case "blue" -> 0xFFFF0000;
            case "yellow" -> 0xFF00FFFF;
            case "cyan" -> 0xFFFFFF00;
            case "magenta" -> 0xFFFF00FF;
            case "black" -> 0xFF000000;
            case "white" -> 0xFFFFFFFF;
            case "gray", "grey" -> 0xFF808080;
            case "transparent" -> 0x00000000;
            case "orange" -> 0xFF0080FF;
            case "purple" -> 0xFF800080;
            case "pink" -> 0xFFFFC0CB;
            case "brown" -> 0xFF2A2AA5;
            default -> null;
        };
    }

    /** Semantic colors per theme tag (0 = light, 1 = dark). */
    static Integer themeColor(String role, int tag) {
        if (tag == 1) {
            return switch (role) {
                case "background" -> 0xFF121212;
                case "surface" -> 0xFF1E1E1E;
                case "primary" -> 0xFF86FCBB;
                case "secondary" -> 0xFFC6D603;
                case "text" -> 0xFFFFFFFF;
                case "error" -> 0xFF7966CF;
                default -> null;
            };
        }
        return switch (role) {
            case "background" -> 0xFFFFFFFF;
            case "surface" -> 0xFFF2F2F2;
            case "primary" -> 0xFFEE6200;
            case "secondary" -> 0xFFC6D603;
            case "text" -> 0xFF000000;
            case "error" -> 0xFF20B000;
            default -> null;
        };
    }
}