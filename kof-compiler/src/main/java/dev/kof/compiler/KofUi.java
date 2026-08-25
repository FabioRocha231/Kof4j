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
    static final Type LABEL = new Type.ClassType("kof.ui", "Label", List.of());
    static final Type BUTTON = new Type.ClassType("kof.ui", "Button", List.of());
    static final Type INPUT = new Type.ClassType("kof.ui", "Input", List.of());
    static final Type COLUMN = new Type.ClassType("kof.ui", "Column", List.of());
    static final Type ROW = new Type.ClassType("kof.ui", "Row", List.of());
    static final Type VIEW = new Type.ClassType("kof.ui", "View", List.of());
    static final Type STYLE = new Type.ClassType("kof.ui", "Style", List.of());
    static final Type WINDOW = new Type.ClassType("kof.ui", "Window", List.of());
    static final Type LINK = new Type.ClassType("kof.ui", "Link", List.of());
    static final Type IMAGE = new Type.ClassType("kof.ui", "Image", List.of());
    static final Type ICON = new Type.ClassType("kof.ui", "Icon", List.of());
    static final Type FONT = new Type.ClassType("kof.ui", "Font", List.of());

    static boolean isColor(Type t) { return COLOR.equals(t); }
    static boolean isTheme(Type t) { return THEME.equals(t); }
    static boolean isLabel(Type t) { return LABEL.equals(t); }
    static boolean isButton(Type t) { return BUTTON.equals(t); }
    static boolean isInput(Type t) { return INPUT.equals(t); }
    static boolean isColumn(Type t) { return COLUMN.equals(t); }
    static boolean isRow(Type t) { return ROW.equals(t); }
    static boolean isView(Type t) { return VIEW.equals(t); }
    static boolean isStyle(Type t) { return STYLE.equals(t); }
    static boolean isWindow(Type t) { return WINDOW.equals(t); }
    static boolean isLink(Type t) { return LINK.equals(t); }
    static boolean isImage(Type t) { return IMAGE.equals(t); }
    static boolean isIcon(Type t) { return ICON.equals(t); }
    static boolean isFont(Type t) { return FONT.equals(t); }
    /** Widget que aceita .setFont(font)/.font */
    static boolean acceptsFont(Type t) {
        return isLabel(t) || isButton(t) || isInput(t) || isView(t) || isLink(t);
    }

    static boolean isUiType(Type t) {
        return isColor(t) || isTheme(t) || isLabel(t) || isButton(t) || isInput(t)
                || isColumn(t) || isRow(t) || isView(t) || isStyle(t) || isWindow(t)
                || isLink(t) || isImage(t) || isIcon(t) || isFont(t);
    }

    static boolean isConstructor(String name) {
        return "Color".equals(name) || "Theme".equals(name)
                || "Label".equals(name) || "Button".equals(name) || "Input".equals(name)
                || "Column".equals(name) || "Row".equals(name) || "View".equals(name)
                || "Style".equals(name) || "Window".equals(name)
                || "Link".equals(name) || "Image".equals(name)
                || "Icon".equals(name) || "Font".equals(name);
    }

    static Type constructorType(String name) {
        if ("Color".equals(name)) return COLOR;
        if ("Link".equals(name)) return LINK;
        if ("Image".equals(name)) return IMAGE;
        if ("Icon".equals(name)) return ICON;
        if ("Font".equals(name)) return FONT;
        return Type.UnknownType.UNKNOWN;
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
        if (isWindow(receiver)) {
            return switch (name) {
                case "title" -> argCount == 0 ? new UiCall("kof_ui_window_title", STR, List.of()) : null;
                case "bind" -> argCount == 1 ? new UiCall("kof_ui_window_bind", Type.PrimitiveType.VOID, List.of(INT)) : null;
                case "show" -> argCount == 0 ? new UiCall("kof_ui_window_show", Type.PrimitiveType.VOID, List.of()) : null;
                case "close" -> argCount == 0 ? new UiCall("kof_ui_window_close", Type.PrimitiveType.VOID, List.of()) : null;
                case "size" -> argCount == 2 ? new UiCall("kof_ui_window_set_size", Type.PrimitiveType.VOID, List.of(INT, INT)) : null;
                default -> null;
            };
        }
        if (isLabel(receiver)) {
            return switch (name) {
                case "text" -> argCount == 0 ? new UiCall("kof_ui_label_text", STR, List.of()) : null;
                case "setText" -> argCount == 1 ? new UiCall("kof_ui_label_set_text", Type.PrimitiveType.VOID, List.of(STR)) : null;
                case "fontSize" -> argCount == 0 ? new UiCall("kof_ui_label_font_size", INT, List.of()) : null;
                case "setFontSize" -> argCount == 1 ? new UiCall("kof_ui_label_set_font_size", Type.PrimitiveType.VOID, List.of(INT)) : null;
                case "bold" -> argCount == 0 ? new UiCall("kof_ui_label_bold", BOOL, List.of()) : null;
                case "setBold" -> argCount == 1 ? new UiCall("kof_ui_label_set_bold", Type.PrimitiveType.VOID, List.of(BOOL)) : null;
                case "color" -> argCount == 0 ? new UiCall("kof_ui_label_color", COLOR, List.of()) : null;
                case "setColor" -> argCount == 1 ? new UiCall("kof_ui_label_set_color", Type.PrimitiveType.VOID, List.of(COLOR)) : null;
                case "remove" -> argCount == 0 ? new UiCall("kof_ui_label_remove", Type.PrimitiveType.VOID, List.of()) : null;
                default -> null;
            };
        }
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
        if (isButton(receiver)) {
            return switch (name) {
                case "text" -> argCount == 0 ? new UiCall("kof_ui_button_text", STR, List.of()) : null;
                case "setText" -> argCount == 1 ? new UiCall("kof_ui_button_set_text", Type.PrimitiveType.VOID, List.of(STR)) : null;
                case "remove" -> argCount == 0 ? new UiCall("kof_ui_button_remove", Type.PrimitiveType.VOID, List.of()) : null;
                default -> null;
            };
        }
        if (isInput(receiver)) {
            return switch (name) {
                case "text" -> argCount == 0 ? new UiCall("kof_ui_input_text", STR, List.of()) : null;
                case "setText" -> argCount == 1 ? new UiCall("kof_ui_input_set_text", Type.PrimitiveType.VOID, List.of(STR)) : null;
                case "remove" -> argCount == 0 ? new UiCall("kof_ui_input_remove", Type.PrimitiveType.VOID, List.of()) : null;
                default -> null;
            };
        }
        if (isView(receiver)) {
            return switch (name) {
                case "bind" -> argCount == 1 ? new UiCall("kof_ui_view_bind", Type.PrimitiveType.VOID, List.of(INT)) : null;
                case "remove" -> argCount == 0 ? new UiCall("kof_ui_view_remove", Type.PrimitiveType.VOID, List.of()) : null;
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
        if (isLink(receiver)) {
            return switch (name) {
                case "text" -> argCount == 0 ? new UiCall("kof_ui_link_text", STR, List.of()) : null;
                case "setText" -> argCount == 1 ? new UiCall("kof_ui_link_set_text", Type.PrimitiveType.VOID, List.of(STR)) : null;
                case "url" -> argCount == 0 ? new UiCall("kof_ui_link_url", STR, List.of()) : null;
                case "setUrl" -> argCount == 1 ? new UiCall("kof_ui_link_set_url", Type.PrimitiveType.VOID, List.of(STR)) : null;
                case "remove" -> argCount == 0 ? new UiCall("kof_ui_link_remove", Type.PrimitiveType.VOID, List.of()) : null;
                default -> null;
            };
        }
        if (isImage(receiver)) {
            return switch (name) {
                case "src" -> argCount == 0 ? new UiCall("kof_ui_image_src", STR, List.of()) : null;
                case "setSrc" -> argCount == 1 ? new UiCall("kof_ui_image_set_src", Type.PrimitiveType.VOID, List.of(STR)) : null;
                case "remove" -> argCount == 0 ? new UiCall("kof_ui_image_remove", Type.PrimitiveType.VOID, List.of()) : null;
                default -> null;
            };
        }
        if (isIcon(receiver)) {
            return switch (name) {
                case "name" -> argCount == 0 ? new UiCall("kof_ui_icon_name", STR, List.of()) : null;
                case "setName" -> argCount == 1 ? new UiCall("kof_ui_icon_set_name", Type.PrimitiveType.VOID, List.of(STR)) : null;
                case "size" -> argCount == 0 ? new UiCall("kof_ui_icon_size", INT, List.of()) : null;
                case "setSize" -> argCount == 1 ? new UiCall("kof_ui_icon_set_size", Type.PrimitiveType.VOID, List.of(INT)) : null;
                case "remove" -> argCount == 0 ? new UiCall("kof_ui_icon_remove", Type.PrimitiveType.VOID, List.of()) : null;
                default -> null;
            };
        }
        if (acceptsFont(receiver)) {
            return switch (name) {
                case "font" -> argCount == 0 ? new UiCall("kof_ui_widget_font", FONT, List.of()) : null;
                case "setFont" -> argCount == 1 ? new UiCall("kof_ui_widget_set_font", Type.PrimitiveType.VOID, List.of(INT)) : null;
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