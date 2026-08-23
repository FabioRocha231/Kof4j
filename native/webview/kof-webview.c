/*
 * kof-webview — native webview shell for Kof (Linux).
 *
 * Opens an embedded WebKitWebView window rendering the HTML page produced
 * by the KofJS backend (kof-ui.html). This is the "native webview" the Kof
 * distribution carries: a real browser engine (WebKitGTK) embedded in a
 * native window, with no Java UI toolkit involved.
 *
 * Usage: kof-webview <html-file> [title]
 *
 * Built with:
 *   gcc $(pkg-config --cflags --libs webkit2gtk-4.1) kof-webview.c -o kof-webview
 *
 * Runtime dependency: libwebkit2gtk-4.1 (or -4.0) and GTK3 on the target
 * system. When the shim is not available, kof run --target=js falls back to
 * opening the page in the system default browser.
 */
#include <gtk/gtk.h>
#include <webkit2/webkit2.h>
#include <string.h>

static void on_destroy(GtkWidget *widget, gpointer data) {
    (void) widget;
    (void) data;
    gtk_main_quit();
}

int main(int argc, char **argv) {
    gtk_init(&argc, &argv);

    if (argc < 2) {
        g_printerr("usage: kof-webview <html-file> [title]\n");
        return 1;
    }
    const char *file = argv[1];
    const char *title = argc > 2 && strlen(argv[2]) > 0 ? argv[2] : "Kof";

    GtkWidget *window = gtk_window_new(GTK_WINDOW_TOPLEVEL);
    gtk_window_set_title(GTK_WINDOW(window), title);
    gtk_window_set_default_size(GTK_WINDOW(window), 900, 600);
    g_signal_connect(window, "destroy", G_CALLBACK(on_destroy), NULL);

    WebKitWebView *view = WEBKIT_WEB_VIEW(webkit_web_view_new());
    gtk_container_add(GTK_CONTAINER(window), GTK_WIDGET(view));

    gchar *uri = g_filename_to_uri(file, NULL, NULL);
    if (uri == NULL) {
        g_printerr("kof-webview: cannot resolve file URI for %s\n", file);
        return 1;
    }
    webkit_web_view_load_uri(view, uri);
    g_free(uri);

    gtk_widget_show_all(window);
    gtk_main();
    return 0;
}