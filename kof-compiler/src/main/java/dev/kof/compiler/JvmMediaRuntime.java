package dev.kof.compiler;

/**
 * Runtime de kof.media — imagens (javax.imageio), áudio/WAV e microfone
 * (javax.sound.sampled). Gerado no KofRuntime junto com JvmRuntime;
 * separado em arquivo próprio pelo mesmo motivo do JvmWebRuntime (limite
 * de 65535 bytes por string constant pool).
 *
 * Filosofia: a linguagem NÃO transporta imagem/áudio como String gigante
 * (nem base64 literal no fonte, nem data-URI colado à mão). O app trata o
 * ARQUIVO: abre, manipula, salva. Web server entrega o arquivo do disco
 * com content-type correto (serveDir). O data-URI só existe como opção
 * explícita (img.dataUri()) para o caso em que o destino só aceita URI.
 */
final class JvmMediaRuntime {

    private JvmMediaRuntime() {}

    static String source() {
        return """
                // ── kof.media — imagens, áudio, microfone ─────────────

                public static final class KofAudioData {
                    final byte[] pcm;          // PCM_SIGNED 16-bit little-endian
                    final int sampleRate;      // Hz
                    final int channels;
                    KofAudioData(byte[] pcm, int sampleRate, int channels) {
                        this.pcm = pcm;
                        this.sampleRate = sampleRate;
                        this.channels = channels;
                    }
                    int durationMs() {
                        if (sampleRate <= 0 || channels <= 0) return 0;
                        long frames = pcm.length / (2L * channels);
                        return (int) (frames * 1000L / sampleRate);
                    }
                }

                public static final class KofImageFile {
                    final java.awt.image.BufferedImage image;
                    final String format;
                    final String path;
                    KofImageFile(java.awt.image.BufferedImage image, String format, String path) {
                        this.image = image;
                        this.format = format;
                        this.path = path;
                    }
                }

                private static final java.util.concurrent.ConcurrentHashMap<Integer, KofImageFile> KOF_MEDIA_IMAGES =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.ConcurrentHashMap<Integer, KofAudioData> KOF_MEDIA_AUDIO =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.atomic.AtomicInteger KOF_MEDIA_SEQ =
                        new java.util.concurrent.atomic.AtomicInteger();

                /** Raiz do projeto (definida pelo CLI: -Dkof.root) — caminhos
                 *  relativos do app resolvem contra ela, não contra o CWD. */
                public static String kof_media_root() {
                    String root = System.getProperty("kof.root");
                    return root == null || root.isBlank()
                            ? System.getProperty("user.dir", ".") : root;
                }

                private static java.nio.file.Path kof_media_resolve(String p) {
                    java.nio.file.Path path = java.nio.file.Path.of(p);
                    if (path.isAbsolute()) return path.toAbsolutePath().normalize();
                    return java.nio.file.Path.of(kof_media_root())
                            .toAbsolutePath().normalize().resolve(p).normalize();
                }

                private static String kof_media_format(String fileName) {
                    String n = fileName == null ? "" : fileName.toLowerCase();
                    int dot = n.lastIndexOf('.');
                    if (dot < 0) return "png";
                    String ext = n.substring(dot + 1);
                    if (ext.equals("jpg") || ext.equals("jpeg")) return "jpeg";
                    if (ext.equals("gif")) return "gif";
                    if (ext.equals("bmp")) return "bmp";
                    if (ext.equals("webp")) return "webp";
                    return "png";
                }

                // ── Bitmap ────────────────────────────────────────────

                public static int kof_media_image_open(String path) {
                    try {
                        java.nio.file.Path p = kof_media_resolve(path);
                        if (!java.nio.file.Files.isRegularFile(p)) {
                            throw new RuntimeException("arquivo não encontrado: " + path);
                        }
                        java.awt.image.BufferedImage img =
                                javax.imageio.ImageIO.read(p.toFile());
                        if (img == null) {
                            throw new RuntimeException(
                                    "formato de imagem não suportado: " + path);
                        }
                        int id = KOF_MEDIA_SEQ.incrementAndGet();
                        KOF_MEDIA_IMAGES.put(id, new KofImageFile(img,
                                kof_media_format(p.getFileName().toString()), path));
                        return id;
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("Image.open falhou: " + e.getMessage(), e);
                    }
                }

                private static KofImageFile kof_media_image(int id) {
                    KofImageFile f = KOF_MEDIA_IMAGES.get(id);
                    if (f == null) throw new IllegalStateException("imagem inválida: " + id);
                    return f;
                }

                public static int kof_media_image_width(int id) {
                    return kof_media_image(id).image.getWidth();
                }

                public static int kof_media_image_height(int id) {
                    return kof_media_image(id).image.getHeight();
                }

                public static String kof_media_image_format(int id) {
                    return kof_media_image(id).format;
                }

                public static int kof_media_image_save(int id, String path) {
                    return kof_media_image_save_fmt(id, path,
                            kof_media_format(java.nio.file.Path.of(path).getFileName().toString()));
                }

                public static int kof_media_image_save_fmt(int id, String path, String fmt) {
                    try {
                        java.nio.file.Path p = kof_media_resolve(path);
                        if (p.getParent() != null) {
                            java.nio.file.Files.createDirectories(p.getParent());
                        }
                        boolean ok = javax.imageio.ImageIO.write(
                                kof_media_image(id).image, fmt, p.toFile());
                        if (!ok) {
                            throw new RuntimeException(
                                    "sem writer para o formato '" + fmt + "'");
                        }
                        return 1;
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("Image.save falhou: " + e.getMessage(), e);
                    }
                }

                /** data-URI gerado EM RUNTIME a partir do arquivo (não é um
                 *  literal no fonte) — para destinos que só aceitam URI. */
                public static String kof_media_image_data_uri(int id) {
                    try {
                        KofImageFile f = kof_media_image(id);
                        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                        if (!javax.imageio.ImageIO.write(f.image, f.format, bos)) {
                            throw new RuntimeException(
                                    "sem writer para o formato '" + f.format + "'");
                        }
                        return "data:image/" + f.format + ";base64,"
                                + java.util.Base64.getEncoder().encodeToString(bos.toByteArray());
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("Image.dataUri falhou: " + e.getMessage(), e);
                    }
                }

                public static int[] kof_media_image_bytes(int id) {
                    return kof_media_image_bytes_fmt(id, kof_media_image(id).format);
                }

                public static int[] kof_media_image_bytes_fmt(int id, String fmt) {
                    try {
                        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                        if (!javax.imageio.ImageIO.write(kof_media_image(id).image, fmt, bos)) {
                            throw new RuntimeException(
                                    "sem writer para o formato '" + fmt + "'");
                        }
                        byte[] b = bos.toByteArray();
                        int[] out = new int[b.length];
                        for (int i = 0; i < b.length; i++) out[i] = b[i];
                        return out;
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("Image.bytes falhou: " + e.getMessage(), e);
                    }
                }

                public static void kof_media_image_close(int id) {
                    KOF_MEDIA_IMAGES.remove(id);
                }

                // ── Audio (WAV) ───────────────────────────────────────

                public static int kof_media_audio_open_wav(String path) {
                    try {
                        java.nio.file.Path p = kof_media_resolve(path);
                        return kof_media_audio_store(kof_media_read_wav(p));
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("Audio.openWav falhou: " + e.getMessage(), e);
                    }
                }

                public static int kof_media_audio_sample_rate(int id) {
                    return kof_media_audio(id).sampleRate;
                }

                public static int kof_media_audio_duration_ms(int id) {
                    return kof_media_audio(id).durationMs();
                }

                public static int kof_media_audio_save_wav(int id, String path) {
                    try {
                        java.nio.file.Path p = kof_media_resolve(path);
                        if (p.getParent() != null) {
                            java.nio.file.Files.createDirectories(p.getParent());
                        }
                        java.nio.file.Files.write(p, kof_media_write_wav(kof_media_audio(id)));
                        return 1;
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("Audio.saveWav falhou: " + e.getMessage(), e);
                    }
                }

                public static int[] kof_media_audio_pcm_bytes(int id) {
                    byte[] pcm = kof_media_audio(id).pcm;
                    int[] out = new int[pcm.length];
                    for (int i = 0; i < pcm.length; i++) out[i] = pcm[i];
                    return out;
                }

                /** Constrói um Audio a partir de PCM 16-bit (teste/síntese). */
                public static int kof_media_audio_from_pcm_bytes(int[] pcm, int sampleRate, int channels) {
                    byte[] b = new byte[pcm.length];
                    for (int i = 0; i < pcm.length; i++) b[i] = (byte) pcm[i];
                    return kof_media_audio_store(new KofAudioData(b, sampleRate, channels));
                }

                private static int kof_media_audio_store(KofAudioData a) {
                    int id = KOF_MEDIA_SEQ.incrementAndGet();
                    KOF_MEDIA_AUDIO.put(id, a);
                    return id;
                }

                private static KofAudioData kof_media_audio(int id) {
                    KofAudioData a = KOF_MEDIA_AUDIO.get(id);
                    if (a == null) throw new IllegalStateException("áudio inválido: " + id);
                    return a;
                }

                private static int kof_media_le16(byte[] b, int off) {
                    return (b[off] & 0xFF) | (b[off + 1] & 0xFF) << 8;
                }

                private static int kof_media_le32(byte[] b, int off) {
                    return (b[off] & 0xFF) | (b[off + 1] & 0xFF) << 8
                            | (b[off + 2] & 0xFF) << 16 | (b[off + 3] & 0xFF) << 24;
                }

                /** Lê WAV RIFF — só PCM 16-bit (o resto é gap honesto). */
                private static KofAudioData kof_media_read_wav(java.nio.file.Path p)
                        throws java.io.IOException {
                    byte[] all = java.nio.file.Files.readAllBytes(p);
                    if (all.length < 12
                            || all[0] != 'R' || all[1] != 'I' || all[2] != 'F' || all[3] != 'F'
                            || all[8] != 'W' || all[9] != 'A' || all[10] != 'V' || all[11] != 'E') {
                        throw new RuntimeException("não é um WAV RIFF: " + p);
                    }
                    int channels = 0, sampleRate = 0, bits = 0;
                    byte[] data = null;
                    int pos = 12;
                    while (pos + 8 <= all.length) {
                        String cid = new String(all, pos, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
                        int size = kof_media_le32(all, pos + 4);
                        int body = pos + 8;
                        if (body + size > all.length) break;
                        if ("fmt ".equals(cid)) {
                            int audioFormat = kof_media_le16(all, body);
                            channels = kof_media_le16(all, body + 2);
                            sampleRate = kof_media_le32(all, body + 4);
                            bits = kof_media_le16(all, body + 14);
                            if (audioFormat != 1) {
                                throw new RuntimeException(
                                        "WAV não suportado: codec " + audioFormat + " (precisa de PCM 1)");
                            }
                        } else if ("data".equals(cid)) {
                            data = new byte[size];
                            System.arraycopy(all, body, data, 0, size);
                        }
                        pos = body + size + (size & 1);
                    }
                    if (data == null || sampleRate <= 0 || bits != 16) {
                        throw new RuntimeException(
                                "WAV não suportado: precisa de PCM 16-bit (bits=" + bits + ")");
                    }
                    return new KofAudioData(data, sampleRate, channels);
                }

                private static byte[] kof_media_write_wav(KofAudioData a) {
                    byte[] pcm = a.pcm;
                    int ch = a.channels, rate = a.sampleRate;
                    int byteRate = rate * ch * 2;
                    int blockAlign = ch * 2;
                    byte[] out = new byte[44 + pcm.length];
                    out[0] = 'R'; out[1] = 'I'; out[2] = 'F'; out[3] = 'F';
                    kof_media_put_le32(out, 4, 36 + pcm.length);
                    out[8] = 'W'; out[9] = 'A'; out[10] = 'V'; out[11] = 'E';
                    out[12] = 'f'; out[13] = 'm'; out[14] = 't'; out[15] = ' ';
                    kof_media_put_le32(out, 16, 16);
                    kof_media_put_le16(out, 20, 1);
                    kof_media_put_le16(out, 22, ch);
                    kof_media_put_le32(out, 24, rate);
                    kof_media_put_le32(out, 28, byteRate);
                    kof_media_put_le16(out, 32, blockAlign);
                    kof_media_put_le16(out, 34, 16);
                    out[36] = 'd'; out[37] = 'a'; out[38] = 't'; out[39] = 'a';
                    kof_media_put_le32(out, 40, pcm.length);
                    System.arraycopy(pcm, 0, out, 44, pcm.length);
                    return out;
                }

                private static void kof_media_put_le16(byte[] b, int off, int v) {
                    b[off] = (byte) (v & 0xFF);
                    b[off + 1] = (byte) ((v >> 8) & 0xFF);
                }

                private static void kof_media_put_le32(byte[] b, int off, int v) {
                    b[off] = (byte) (v & 0xFF);
                    b[off + 1] = (byte) ((v >> 8) & 0xFF);
                    b[off + 2] = (byte) ((v >> 16) & 0xFF);
                    b[off + 3] = (byte) ((v >> 24) & 0xFF);
                }

                // ── Mic ───────────────────────────────────────────────

                public static java.util.ArrayList<String> kof_media_mic_list() {
                    java.util.ArrayList<String> out = new java.util.ArrayList<>();
                    for (javax.sound.sampled.Mixer.Info m :
                            javax.sound.sampled.AudioSystem.getMixerInfo()) {
                        out.add(m.getName());
                    }
                    return out;
                }

                private static javax.sound.sampled.AudioFormat kof_media_mic_format() {
                    return new javax.sound.sampled.AudioFormat(
                            javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                            16000, 16, 1, 2, 16000.0f, true);
                }

                /** Captura `seconds` do microfone padrão → Audio (PCM 16k mono). */
                public static int kof_media_mic_record(int seconds) {
                    javax.sound.sampled.AudioFormat fmt = kof_media_mic_format();
                    javax.sound.sampled.DataLine.Info info =
                            new javax.sound.sampled.TargetDataLine.Info(
                                    javax.sound.sampled.TargetDataLine.class, fmt);
                    if (!javax.sound.sampled.AudioSystem.isLineSupported(info)) {
                        throw new RuntimeException(
                                "sem microfone disponível neste ambiente (MEDIA003)");
                    }
                    int total = 16000 * Math.max(1, seconds) * 2;
                    byte[] buf = new byte[total];
                    int read = 0;
                    try (var line = (javax.sound.sampled.TargetDataLine)
                            javax.sound.sampled.AudioSystem.getLine(info)) {
                        line.open(fmt);
                        line.start();
                        while (read < buf.length) {
                            int n = line.read(buf, read, Math.min(2048, buf.length - read));
                            if (n <= 0) break;
                            read += n;
                        }
                        line.stop();
                    } catch (javax.sound.sampled.LineUnavailableException e) {
                        throw new RuntimeException(
                                "microfone indisponível (MEDIA003): " + e.getMessage(), e);
                    }
                    byte[] pcm = new byte[read];
                    System.arraycopy(buf, 0, pcm, 0, read);
                    return kof_media_audio_store(new KofAudioData(pcm, 16000, 1));
                }

                // ── web: serving de arquivos (serveDir) ──────────────

                public static void kof_web_serve_dir(String appId, String prefix, String dir) {
                    String p = prefix.startsWith("/") ? prefix : "/" + prefix;
                    while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
                    java.nio.file.Path d = kof_media_resolve(dir);
                    kof_web_app(appId).staticDirs.add(
                            new WebApp.StaticDir(p, d.toAbsolutePath().normalize()));
                }

                private static String kof_web_mime(String fileName) {
                    String n = fileName == null ? "" : fileName.toLowerCase();
                    int dot = n.lastIndexOf('.');
                    if (dot < 0) return "application/octet-stream";
                    switch (n.substring(dot + 1)) {
                        case "html": case "htm": return "text/html; charset=utf-8";
                        case "css": return "text/css; charset=utf-8";
                        case "js": case "mjs": return "text/javascript; charset=utf-8";
                        case "json": return "application/json; charset=utf-8";
                        case "txt": case "md": case "log": return "text/plain; charset=utf-8";
                        case "xml": case "yaml": case "yml": return "application/xml; charset=utf-8";
                        case "svg": return "image/svg+xml";
                        case "png": return "image/png";
                        case "jpg": case "jpeg": return "image/jpeg";
                        case "gif": return "image/gif";
                        case "webp": return "image/webp";
                        case "ico": return "image/x-icon";
                        case "bmp": return "image/bmp";
                        case "avif": return "image/avif";
                        case "wav": return "audio/wav";
                        case "mp3": return "audio/mpeg";
                        case "ogg": case "oga": return "audio/ogg";
                        case "flac": return "audio/flac";
                        case "mp4": return "video/mp4";
                        case "webm": return "video/webm";
                        case "pdf": return "application/pdf";
                        case "woff": return "font/woff";
                        case "woff2": return "font/woff2";
                        case "ttf": return "font/ttf";
                        case "otf": return "font/otf";
                        case "wasm": return "application/wasm";
                        default: return "application/octet-stream";
                    }
                }

                /** Retorna o arquivo estático para /prefix/... ou null (404).
                 *  Protegido contra path traversal (normaliza e confina). */
                public static byte[] kof_web_static_resolve(WebApp app, String path) {
                    for (WebApp.StaticDir sd : app.staticDirs) {
                        String rel;
                        if (path.equals(sd.prefix)) {
                            rel = "index.html";
                        } else if (path.startsWith(sd.prefix + "/")) {
                            rel = path.substring(sd.prefix.length() + 1);
                        } else {
                            continue;
                        }
                        java.nio.file.Path f = sd.dir.resolve(rel).normalize();
                        if (!f.startsWith(sd.dir)) continue;          // traversal
                        if (!java.nio.file.Files.isRegularFile(f)) continue;
                        try {
                            return java.nio.file.Files.readAllBytes(f);
                        } catch (java.io.IOException e) {
                            continue;
                        }
                    }
                    return null;
                }

                public static String kof_web_static_headers(WebApp app, String path) {
                    for (WebApp.StaticDir sd : app.staticDirs) {
                        String rel;
                        if (path.equals(sd.prefix)) rel = "index.html";
                        else if (path.startsWith(sd.prefix + "/")) rel = path.substring(sd.prefix.length() + 1);
                        else continue;
                        java.nio.file.Path f = sd.dir.resolve(rel).normalize();
                        if (!f.startsWith(sd.dir)) continue;
                        if (!java.nio.file.Files.isRegularFile(f)) continue;
                        return "Content-Type: " + kof_web_mime(f.getFileName().toString())
                                + "\\r\\nCache-Control: public, max-age=86400";
                    }
                    return null;
                }

                public static int kof_web_static_length(WebApp app, String path) {
                    for (WebApp.StaticDir sd : app.staticDirs) {
                        String rel;
                        if (path.equals(sd.prefix)) rel = "index.html";
                        else if (path.startsWith(sd.prefix + "/")) rel = path.substring(sd.prefix.length() + 1);
                        else continue;
                        java.nio.file.Path f = sd.dir.resolve(rel).normalize();
                        if (!f.startsWith(sd.dir)) continue;
                        if (!java.nio.file.Files.isRegularFile(f)) continue;
                        try {
                            return (int) java.nio.file.Files.size(f);
                        } catch (java.io.IOException e) {
                            continue;
                        }
                    }
                    return -1;
                }
                """;
    }
}
