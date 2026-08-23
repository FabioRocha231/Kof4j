/*
 * kof-webview — native webview shell for Kof (Linux).
 *
 * Opens an embedded WebKitWebView window rendering the HTML page produced
 * by the KofJS backend (kof-ui.html). This is the "native webview" the Kof
 * distribution carries: a real browser engine (WebKitGTK) embedded in a
 * native window, with no Java UI toolkit involved.
 *
 * The declarations below are hand-written against the stable C API of
 * GTK3 and WebKitGTK (no dev headers required — only the runtime shared
 * libraries), so the shim can be built on systems that only ship the
 * runtime libs:
 *
 *   gcc -O2 kof-webview.c -o kof-webview \
 *       /usr/lib/x86_64-linux-gnu/libwebkit2gtk-4.1.so.0 \
 *       /usr/lib/x86_64-linux-gnu/libgtk-3.so.0 \
 *       /usr/lib/x86_64-linux-gnu/libgobject-2.0.so.0 \
 *       /usr/lib/x86_64-linux-gnu/libglib-2.0.so.0
 *
 * Usage: kof-webview <html-file> [title]
 *
 * When the shim is not available, kof run --target=js falls back to opening
 * the page in the system default browser.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef void *gpointer;
typedef unsigned long gulong;
typedef void (*GCallback)(void);

extern void gtk_init(int *argc, char ***argv);
extern void gtk_main(void);
extern void gtk_main_quit(void);
extern gpointer gtk_window_new(int type);
extern void gtk_window_set_title(gpointer window, const char *title);
extern void gtk_window_set_default_size(gpointer window, int width, int height);
extern void gtk_container_add(gpointer container, gpointer widget);
extern void gtk_widget_show_all(gpointer widget);
extern gulong g_signal_connect_data(gpointer instance, const char *detailed_signal,
                                    GCallback c_handler, gpointer data,
                                    gpointer destroy_data, int connect_flags);
extern char *g_filename_to_uri(const char *filename, const char *hostname, gpointer *error);
extern void g_free(gpointer mem);

extern gpointer webkit_web_view_new(void);
extern void webkit_web_view_load_uri(gpointer view, const char *uri);
extern gpointer webkit_web_view_get_settings(gpointer view);
extern void webkit_settings_set_allow_file_access_from_file_urls(gpointer settings, int allowed);

#define GTK_WINDOW_TOPLEVEL 0

static void on_destroy(gpointer widget, gpointer data) {
    (void) widget;
    (void) data;
    gtk_main_quit();
}

int main(int argc, char **argv) {
    gtk_init(&argc, &argv);

    if (argc < 2) {
        fprintf(stderr, "usage: kof-webview <html-file> [title]\n");
        return 1;
    }
    const char *file = argv[1];
    const char *title = argc > 2 && strlen(argv[2]) > 0 ? argv[2] : "Kof";

    gpointer window = gtk_window_new(GTK_WINDOW_TOPLEVEL);
    gtk_window_set_title(window, title);
    gtk_window_set_default_size(window, 900, 600);
    g_signal_connect_data(window, "destroy", (GCallback) on_destroy, NULL, NULL, 0);

    gpointer view = webkit_web_view_new();
    gtk_container_add(window, view);

    // ES modules over file:// are blocked by the engine's CORS rules by
    // default; the KofJS app (index.html + *.mjs) must be allowed to import
    // its runtime modules from the same directory.
    gpointer settings = webkit_web_view_get_settings(view);
    if (settings != NULL) {
        webkit_settings_set_allow_file_access_from_file_urls(settings, 1);
    }

    gpointer error = NULL;
    char *uri = g_filename_to_uri(file, NULL, &error);
    if (uri == NULL) {
        fprintf(stderr, "kof-webview: cannot resolve file URI for %s\n", file);
        return 1;
    }
    webkit_web_view_load_uri(view, uri);
    g_free(uri);

    gtk_widget_show_all(window);
    gtk_main();
    return 0;
}