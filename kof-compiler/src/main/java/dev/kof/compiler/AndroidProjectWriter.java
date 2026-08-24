package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * AndroidProjectWriter — Fase 1 do target kof-android (ver
 * docs/targets/KOFANDROID.md): transforma a saída do backend JVM num
 * projeto Gradle pronto para `gradle assembleDebug`.
 *
 * Layout gerado em outputDir:
 *
 *   settings.gradle.kts / build.gradle.kts / gradle.properties
 *   app/build.gradle.kts
 *   app/src/main/AndroidManifest.xml
 *   app/src/main/java/dev/kof/app/MainActivity.java   (host WebView)
 *   app/src/main/assets/kof/                          (saída KofJS)
 *   libs/kof-app.jar                                  (bytecode das classes Kof)
 *
 * Nada aqui executa código do usuário: o Gradle compila a MainActivity
 * contra o SDK real e o d8 converte o jar para dex.
 */
final class AndroidProjectWriter {

    private static final String APP_PACKAGE = "dev.kof.app";
    private static final String APP_LABEL = "Kof App";

    /**
     * Escreve o projeto. As classes JVM já estão em outputDir (o JvmBackend
     * rodou antes); a versão KofJS é emitida aqui para os assets do WebView
     * — a MESMA camada de render widgets→DOM usada no desktop.
     */
    void write(Path outputDir, IRModule module) throws IOException {
        Files.createDirectories(outputDir);

        // 1. assets: mesma IR → KofJS (source maps habilitados para o
        //    chrome://inspect depurar o WebView)
        Path assets = outputDir.resolve("app/src/main/assets/kof");
        Files.createDirectories(assets);
        new JsBackend().emit(module, assets, true);

        // 2. libs/kof-app.jar: bytecode das classes do programa (+ helpers)
        Path libs = outputDir.resolve("libs");
        Files.createDirectories(libs);
        writeJar(outputDir, libs.resolve("kof-app.jar"));

        // 3. projeto Gradle + host Activity
        writeGradleFiles(outputDir);
        writeManifest(outputDir);
        writeMainActivity(outputDir);

        // 4. instruções honestas na raiz — sem mágica
        Files.writeString(outputDir.resolve("README.txt"), """
                Projeto Android gerado pelo Kof (target android).

                Pré-requisito: Android SDK (API 34) com AGP 8.5+ e JDK 21.

                    cd %s
                    gradle assembleDebug
                    gradle installDebug

                O APK fica em app/build/outputs/apk/debug/.

                - A UI (kof.ui) renderiza via WebView carregando assets/kof/.
                - Classes Kof que extendem componentes android.* vão no jar;
                  use android.jar no ExternalClasspath da compilação.
                """.formatted(APP_PACKAGE));
    }

    /** Empacota todos os .class sob classesDir no jar (d8 consome direto). */
    private void writeJar(Path classesDir, Path jarFile) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jarFile));
             Stream<Path> walk = Files.walk(classesDir)) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".class")).toList()) {
                String entry = classesDir.relativize(p).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(Files.readAllBytes(p));
            }
        }
    }

    private void writeGradleFiles(Path out) throws IOException {
        Files.writeString(out.resolve("settings.gradle.kts"), """
                pluginManagement {
                    repositories {
                        google()
                        mavenCentral()
                        gradlePluginPortal()
                    }
                }
                dependencyResolutionManagement {
                    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                    repositories {
                        google()
                        mavenCentral()
                    }
                }
                rootProject.name = "kof-app"
                include(":app")
                """);

        Files.writeString(out.resolve("build.gradle.kts"), """
                plugins {
                    id("com.android.application") version "8.5.2" apply false
                }
                """);

        Files.writeString(out.resolve("gradle.properties"), """
                org.gradle.jvmargs=-Xmx2048m
                android.useAndroidX=false
                android.nonTransitiveRClass=true
                """);

        Files.writeString(out.resolve("app/build.gradle.kts"), """
                plugins {
                    id("com.android.application")
                }

                android {
                    namespace = "%s"
                    compileSdk = 34

                    defaultConfig {
                        applicationId = "%s"
                        minSdk = 24
                        targetSdk = 34
                        versionCode = 1
                        versionName = "0.0.14-alpha"
                    }

                    // o jar contém bytecode Java 21 emitido pelo backend JVM;
                    // o d8 do AGP faz o desugaring para o ART
                    compileOptions {
                        sourceCompatibility = JavaVersion.VERSION_21
                        targetCompatibility = JavaVersion.VERSION_21
                    }
                }

                dependencies {
                    implementation(files("../libs/kof-app.jar"))
                }
                """.formatted(APP_PACKAGE, APP_PACKAGE));
    }

    private void writeManifest(Path out) throws IOException {
        Path manifest = out.resolve("app/src/main/AndroidManifest.xml");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">

                    <application
                        android:label="%s"
                        android:theme="@android:style/Theme.Material.Light.NoActionBar">
                        <activity
                            android:name=".MainActivity"
                            android:exported="true"
                            android:configChanges="orientation|screenSize|keyboardHidden">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """.formatted(APP_LABEL));
    }

    /**
     * Host sintetizado: um WebView fullscreen carregando os assets KofJS.
     * O usuário nunca escreve Activity — a intenção continua sendo
     * `Window("...")`; este arquivo é mecanismo do target.
     */
    private void writeMainActivity(Path out) throws IOException {
        Path java = out.resolve("app/src/main/java/" + APP_PACKAGE.replace('.', '/') + "/MainActivity.java");
        Files.createDirectories(java.getParent());
        Files.writeString(java, """
                package %s;

                import android.app.Activity;
                import android.os.Bundle;
                import android.webkit.WebSettings;
                import android.webkit.WebView;

                public class MainActivity extends Activity {
                    private WebView webView;

                    @Override
                    public void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        webView = new WebView(this);
                        WebSettings settings = webView.getSettings();
                        settings.setJavaScriptEnabled(true);
                        settings.setDomStorageEnabled(true);
                        settings.setAllowFileAccess(true);
                        // ES modules vindo de file:// exigem as duas flags
                        // abaixo (deprecated mas sem dependência androidx)
                        settings.setAllowFileAccessFromFileURLs(true);
                        settings.setAllowUniversalAccessFromFileURLs(true);
                        setContentView(webView);
                        webView.loadUrl("file:///android_asset/kof/index.html");
                    }

                    @Override
                    protected void onDestroy() {
                        if (webView != null) webView.destroy();
                        super.onDestroy();
                    }
                }
                """.formatted(APP_PACKAGE));
    }
}
