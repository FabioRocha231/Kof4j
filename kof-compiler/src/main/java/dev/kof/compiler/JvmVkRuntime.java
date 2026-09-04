package dev.kof.compiler;

/**
 * M32.1: FFI Vulkan compute — FFM (java.lang.foreign, JDK 21+).
 *
 * Cadeia completa: vkCreateInstance → vkEnumeratePhysicalDevices (2-pass) →
 * vkCreateDevice (com pEnabledFeatures explícito) → vkGetDeviceQueue →
 * vkCreateShaderModule (SPIR-V de gpu/shaders/*.spv) → descriptor set
 * layout (N storage buffers) → pipeline layout (push constants 12B) →
 * vkCreateComputePipelines (stage EMBUTIDO no VkComputePipelineCreateInfo
 * — memcpy do stage p/ offset 24, NÃO ponteiro!) → buffers host-visible
 * (HOST_VISIBLE|HOST_COHERENT via vkMapMemory) → descriptor pool/set →
 * command buffer + dispatch + fence.
 *
 * Lições do debug (2026-08-30, C puro + FFM confirmam os mesmos sintomas):
 * - stage é struct inline no ComputePipelineCreateInfo (offset 24, 48B)
 * - VkDeviceCreateInfo sizeof 72; pEnabledFeatures@32 (NULL → alguns drivers falham)
 * - VkMemoryAllocateInfo: allocationSize@16, memoryTypeIndex@24 (sizeof 32)
 * - VkWriteDescriptorSet sizeof 64 (pTexelBufferView@56)
 * - VkSubmitInfo sizeof 72 (pCommandBuffers@48)
 * - VkMemoryType = 8B (props@4+8t no PhysicalDeviceMemoryProperties)
 * - RADV 25.2.8 (Polaris12) e llvmpipe 25.2.8 deste ambiente crasham no
 *   vkCmdDispatch / ignoram a escrita do kernel — bug de ambiente, não do
 *   FFI (C puro dlsym reproduz). Fallback CPU cobre o runtime: nunca derruba.
 *
 * Launcher Kof já roda com --enable-native-access=ALL-UNNAMED.
 * kof_vk_available() degrada para false em qualquer falha.
 */
final class JvmVkRuntime {
    private JvmVkRuntime() {}

    static String source() {
        return VK_SOURCE;
    }

    private static final String VK_SOURCE = """
                // ── kof.vulkan — compute FFI via libvkchain.so (M32.3) ─────────
                // A cadeia completa (instance→device→pipeline→buffers→dispatch)
                // vive na libvkchain.so (C, RADV validado). Aqui: 3 downcalls
                // FFM simples — sem structs Vulkan no Java. Qualquer falha
                // degrada p/ false → goldens CPU. O programa nunca cai.
                private static volatile boolean VK_INITED = false;
                private static volatile boolean VK_OK = false;
                private static String VK_ERR = "not initialized";
                private static java.lang.invoke.MethodHandle VK_INIT;
                private static java.lang.invoke.MethodHandle VK_DISP;
                private static java.lang.invoke.MethodHandle VK_DISP64;
                private static java.lang.invoke.MethodHandle VK_REASON;
                private static java.lang.invoke.MethodHandle VK_INIT64_REASON;
                private static java.lang.invoke.MethodHandle VK_INIT64;
                private static volatile boolean VK64_INITED = false;
                private static volatile boolean VK64_OK = false;
                // M36 FASE C: API matvec residente (vkchain64 v2: set_shape +
                // mapped_w + matvec; W fica no buffer host-visible mapeado —
                // a GPU lê via PCIe sem copia por dispatch)
                private static java.lang.invoke.MethodHandle MV64_SETSHAPE;
                private static java.lang.invoke.MethodHandle MV64_LOADW;
                private static java.lang.invoke.MethodHandle MV64_MATVEC;
                private static java.lang.invoke.MethodHandle MV64_WPUT;
                private static java.lang.invoke.MethodHandle MV64_WRUN;
                private static java.lang.invoke.MethodHandle MV64_WPUT32;
                private static java.lang.invoke.MethodHandle MV64_WRUN32;
                private static java.lang.invoke.MethodHandle MV64_WPUTSP;
                private static java.lang.invoke.MethodHandle MV64_WRUNSP;
                private static java.lang.invoke.MethodHandle MV64_REASON;
                private static volatile boolean MV64_INITED = false;
                private static volatile boolean MV64_OK = false;
                private static volatile int MV64_CURM = 0;
                private static volatile int MV64_CURK = 0;

                public static boolean kof_vk_available() {
                    if (!VK_INITED) {
                        try {
                            VK_OK = kof_vk_init();
                        } catch (Throwable t) {
                            VK_OK = false;
                            VK_ERR = t.getClass().getSimpleName() + ": " + t.getMessage();
                        }
                        VK_INITED = true;
                    }
                    return VK_OK;
                }

                public static String kof_vk_fail_reason() {
                    if (VK_REASON != null) {
                        String r = readCString(VK_REASON);
                        if (r != null) return r;
                    }
                    return VK_ERR;
                }

                // matmul C[M×N] = A[M×K]×B[K×N]; ponto fixo de milésimos.
                // Retorna 0 em sucesso; != 0 → caller usa o golden CPU.
                public static int kof_vk_dispatch(int[] a, int[] b, int[] c,
                                                  int m, int n, int k) {
                    if (!kof_vk_available() || VK_DISP == null) return -1;
                    try {
                        return (int) VK_DISP.invoke(a, b, c, m, n, k);
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                // M36: matmul int64 — acumulador long sem overflow (koflama
                // NANO: produto unitário 9.3e18 > int32). libvkchain64.so +
                // matmul64.spv (buffers ivec2 hi/lo). Golden CPU no caller.
                // FFM não passa heap array como pointer: copia p/ segment nativo
                private static java.lang.foreign.MemorySegment seg64(long[] v,
                        java.lang.foreign.Arena arena) {
                    var seg = arena.allocate((long) v.length * 8);
                    java.lang.foreign.MemorySegment.copy(java.lang.foreign.MemorySegment.ofArray(v),
                            0, seg, 0, (long) v.length * 8);
                    return seg;
                }

                public static int kof_vk_dispatch64(long[] a, long[] b, long[] c,
                                                    int m, int n, int k) {
                    kof_vk_available(); // lazy: carrega handles das DUAS libs (32 e 64)
                    if (!kof_vk64_ready() || VK_DISP64 == null) return -1;
                    try {
                        var arena = java.lang.foreign.Arena.ofConfined();
                        var sa = seg64(a, arena);
                        var sb = seg64(b, arena);
                        var sc = seg64(c, arena);
                        int rc = (int) VK_DISP64.invoke(sa, sb, sc, m, n, k);
                        if (rc == 0) {
                            java.lang.foreign.MemorySegment.copy(sc, 0,
                                    java.lang.foreign.MemorySegment.ofArray(c), 0,
                                    (long) c.length * 8);
                        }
                        arena.close();
                        return rc;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                // M36 FASE C: matvec residente — vkchain64 v2 (matvec64.spv).
                // W por call: load_w copia p/ buffer mapeado (DMA); x/y 8B-elem.
                public static int kof_mv64_set_shape(int m, int k) {
                    if (!kof_mv64_ready() || MV64_SETSHAPE == null) return -1;
                    try {
                        int rc = (int) MV64_SETSHAPE.invoke(m, k);
                        if (rc == 0) {
                            MV64_CURM = m;
                            MV64_CURK = k;
                        }
                        return rc;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_load_w(long[] w, int m, int k) {
                    if (!kof_mv64_ready() || MV64_LOADW == null) return -1;
                    try {
                        var arena = java.lang.foreign.Arena.ofConfined();
                        var seg = arena.allocate((long) w.length * 8);
                        java.lang.foreign.MemorySegment.copy(java.lang.foreign.MemorySegment.ofArray(w),
                                0, seg, 0, (long) w.length * 8);
                        int rc = (int) MV64_LOADW.invoke(seg, m, k);
                        arena.close();
                        return rc;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                // FASE C2b: W residente por id — wput UMA vez por peso (boot),
                // wrun por matvec sem nova copia de W
                public static int kof_mv64_wput(int id, long[] w, int m, int k) {
                    if (!kof_mv64_ready() || MV64_WPUT == null) return -1;
                    try {
                        var arena = java.lang.foreign.Arena.ofConfined();
                        var seg = arena.allocate((long) w.length * 8);
                        java.lang.foreign.MemorySegment.copy(java.lang.foreign.MemorySegment.ofArray(w),
                                0, seg, 0, (long) w.length * 8);
                        int rc = (int) MV64_WPUT.invoke(id, seg, m, k);
                        arena.close();
                        return rc;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_wrun(int id, long[] x, long[] y, int m, int k, long div) {
                    if (!kof_mv64_ready() || MV64_WRUN == null) return -1;
                    try {
                        var arena = java.lang.foreign.Arena.ofConfined();
                        var sx = arena.allocate((long) x.length * 8);
                        java.lang.foreign.MemorySegment.copy(java.lang.foreign.MemorySegment.ofArray(x),
                                0, sx, 0, (long) x.length * 8);
                        var sy = arena.allocate((long) Math.max(m, y.length) * 8);
                        int rc = (int) MV64_WRUN.invoke(id, sx, sy, m, k, div);
                        if (rc == 0) {
                            java.lang.foreign.MemorySegment.copy(sy, 0,
                                    java.lang.foreign.MemorySegment.ofArray(y), 0, (long) m * 8);
                        }
                        arena.close();
                        return rc;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                // M36.1: caminho i32 — W/x/y de 4 bytes (metade do PCIe)
                public static int kof_mv64_wput32(int id, int[] w, int m, int k) {
                    if (!kof_mv64_ready() || MV64_WPUT32 == null) return -1;
                    try {
                        var arena = java.lang.foreign.Arena.ofConfined();
                        var seg = arena.allocate((long) w.length * 4);
                        java.lang.foreign.MemorySegment.copy(java.lang.foreign.MemorySegment.ofArray(w),
                                0, seg, 0, (long) w.length * 4);
                        int rc = (int) MV64_WPUT32.invoke(id, seg, m, k);
                        arena.close();
                        return rc;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_wrun32(int id, long[] x, long[] y, int m, int k, long div) {
                    if (!kof_mv64_ready() || MV64_WRUN32 == null) return -1;
                    try {
                        var arena = java.lang.foreign.Arena.ofConfined();
                        var sx = arena.allocate((long) x.length * 8);
                        java.lang.foreign.MemorySegment.copy(java.lang.foreign.MemorySegment.ofArray(x),
                                0, sx, 0, (long) x.length * 8);
                        var sy = arena.allocate((long) Math.max(m, y.length) * 8);
                        int rc = (int) MV64_WRUN32.invoke(id, sx, sy, m, k, div);
                        if (rc == 0) {
                            java.lang.foreign.MemorySegment.copy(sy, 0,
                                    java.lang.foreign.MemorySegment.ofArray(y), 0, (long) m * 8);
                        }
                        arena.close();
                        return rc;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                // M36.3: caminho split pre-computado (bit-exato com o CPU)
                public static int kof_mv64_wputsp(int id, int[] wh, int[] wl, int m, int k) {
                    if (MV64_WPUTSP == null) return -1;
                    try {
                        var arena = java.lang.foreign.Arena.ofConfined();
                        var sh = arena.allocate((long) wh.length * 4);
                        java.lang.foreign.MemorySegment.copy(java.lang.foreign.MemorySegment.ofArray(wh),
                                0, sh, 0, (long) wh.length * 4);
                        var sl = arena.allocate((long) wl.length * 4);
                        java.lang.foreign.MemorySegment.copy(java.lang.foreign.MemorySegment.ofArray(wl),
                                0, sl, 0, (long) wl.length * 4);
                        int rc = (int) MV64_WPUTSP.invoke(id, sh, sl, m, k);
                        arena.close();
                        return rc;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_wrunsp(int id, long[] x, long[] y, int m, int k, long div) {
                    if (MV64_WRUNSP == null) return -1;
                    try {
                        var arena = java.lang.foreign.Arena.ofConfined();
                        var sx = arena.allocate((long) x.length * 8);
                        java.lang.foreign.MemorySegment.copy(java.lang.foreign.MemorySegment.ofArray(x),
                                0, sx, 0, (long) x.length * 8);
                        var sy = arena.allocate((long) Math.max(m, y.length) * 8);
                        int rc = (int) MV64_WRUNSP.invoke(id, sx, sy, m, k, div);
                        if (rc == 0) {
                            java.lang.foreign.MemorySegment.copy(sy, 0,
                                    java.lang.foreign.MemorySegment.ofArray(y), 0, (long) m * 8);
                        }
                        arena.close();
                        return rc;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_matvec(long[] x, long[] y, int m, int k) {
                    if (!kof_mv64_ready() || MV64_MATVEC == null) return -1;
                    if (MV64_CURM != m || MV64_CURK != k) return -2; // shape divergente
                    try {
                        var arena = java.lang.foreign.Arena.ofConfined();
                        var sx = arena.allocate((long) x.length * 8);
                        java.lang.foreign.MemorySegment.copy(java.lang.foreign.MemorySegment.ofArray(x),
                                0, sx, 0, (long) x.length * 8);
                        var sy = arena.allocate((long) Math.max(m, y.length) * 8);
                        int rc = (int) MV64_MATVEC.invoke(sx, sy, m, k);
                        if (rc == 0) {
                            java.lang.foreign.MemorySegment.copy(sy, 0,
                                    java.lang.foreign.MemorySegment.ofArray(y), 0, (long) m * 8);
                        }
                        arena.close();
                        return rc;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                private static boolean kof_mv64_ready() {
                    if (!MV64_INITED) {
                        try {
                            MV64_OK = kof_mv64_init();
                        } catch (Throwable t) {
                            MV64_OK = false;
                        }
                        MV64_INITED = true;
                    }
                    return MV64_OK;
                }

                private static boolean kof_mv64_init() {
                    if (MV64_SETSHAPE != null) return true; // handles já ligados
                    var arena = java.lang.foreign.Arena.global();
                    var linker = java.lang.foreign.Linker.nativeLinker();
                    var P = java.lang.foreign.ValueLayout.ADDRESS;
                    var I = java.lang.foreign.ValueLayout.JAVA_INT;
                    var J = java.lang.foreign.ValueLayout.JAVA_LONG;
                    java.lang.foreign.SymbolLookup lib = null;
                    try {
                        lib = java.lang.foreign.SymbolLookup.libraryLookup("libvkchain64.so", arena);
                    } catch (Throwable t) {
                        return false;
                    }
                    try {
                        // init(spv_path): SPV do matvec64 (env KOF_GPU_SPV64,
                        // default gpu/shaders/matvec64.spv)
                        var init64 = linker.downcallHandle(lib.find("vkchain64_init").orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(I, P));
                        MV64_REASON = linker.downcallHandle(lib.find("vkchain64_fail_reason").orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(P));
                        MV64_SETSHAPE = linker.downcallHandle(lib.find("vkchain64_set_shape").orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(I, I, I));
                        MV64_MATVEC = linker.downcallHandle(lib.find("vkchain64_matvec").orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(I, P, P, I, I));
                        MV64_WPUT = linker.downcallHandle(lib.find("vkchain64_wput").orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(I, I, P, I, I));
                        MV64_WRUN = linker.downcallHandle(lib.find("vkchain64_wrun").orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(I, I, P, P, I, I, J));
                        MV64_WPUT32 = linker.downcallHandle(lib.find("vkchain64_wput32").orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(I, I, P, I, I));
                        MV64_WRUN32 = linker.downcallHandle(lib.find("vkchain64_wrun32").orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(I, I, P, P, I, I, J));
                        MV64_WPUTSP = linker.downcallHandle(lib.find("vkchain64_wputsp").orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(I, I, P, P, I, I));
                        MV64_WRUNSP = linker.downcallHandle(lib.find("vkchain64_wrunsp").orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(I, I, P, P, I, I, J));
                        String spv = System.getenv("KOF_GPU_SPV64");
                        if (spv == null || spv.isEmpty()) spv = "gpu/shaders/matvec64.spv";
                        int rc = (int) init64.invoke(nativeCstr(arena, spv));
                        if (rc != 0) return false;
                        // load_w(long* w, m, k) — copia p/ buffer mapeado interno
                        MV64_LOADW = linker.downcallHandle(lib.find("vkchain64_load_w").orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(I, P, I, I));
                        return true;
                    } catch (Throwable t) {
                        return false;
                    }
                }

                // init separado da lib64 (vkchain64_init + matmul64.spv):
                // mesma cadeia Vulkan mas com buffers 8B e SPV próprio.
                private static boolean kof_vk64_ready() {
                    if (!VK64_INITED) {
                        try {
                            VK64_OK = kof_vk64_init();
                        } catch (Throwable t) {
                            VK64_OK = false;
                        }
                        VK64_INITED = true;
                    }
                    return VK64_OK;
                }

                private static boolean kof_vk64_init() {
                    if (VK_INIT64 == null) return false;
                    String spv = System.getenv("KOF_GPU_SPV64");
                    if (spv == null || spv.isEmpty()) spv = "gpu/shaders/matmul64.spv";
                    int rc;
                    try {
                        rc = (int) VK_INIT64.invoke(
                                nativeCstr(java.lang.foreign.Arena.global(), spv));
                    } catch (Throwable t) {
                        return false;
                    }
                    return rc == 0;
                }

                // C string NUL-terminated em memória NATIVA (heap segment é
                // rejeitado em downcalls no JDK 22+/25)
                private static java.lang.foreign.MemorySegment nativeCstr(
                        java.lang.foreign.Arena arena, String s) {
                    byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    byte[] nul = new byte[b.length + 1];
                    System.arraycopy(b, 0, nul, 0, b.length);
                    var seg = arena.allocate(nul.length);
                    java.lang.foreign.MemorySegment.copy(java.lang.foreign.MemorySegment.ofArray(nul),
                            0, seg, 0, nul.length);
                    return seg;
                }

                private static boolean kof_vk_init() {
                    var arena = java.lang.foreign.Arena.global();
                    var linker = java.lang.foreign.Linker.nativeLinker();
                    var P = java.lang.foreign.ValueLayout.ADDRESS;
                    var I = java.lang.foreign.ValueLayout.JAVA_INT;
                    // M36: int64 em lib separada (libvkchain64.so); loader via
                    // LD_LIBRARY_PATH ou /usr/local/lib (build.sh copia com sudo).
                    java.lang.foreign.SymbolLookup lib = null;
                    String err64 = "";
                    for (String name : new String[]{"libvkchain64.so"}) {
                        try {
                            lib = java.lang.foreign.SymbolLookup.libraryLookup(name, arena);
                            break;
                        } catch (Throwable t) {
                            err64 = t.getMessage();
                        }
                    }
                    if (lib != null) {
                        try {
                            VK_DISP64 = linker.downcallHandle(lib.find("vkchain64_dispatch").orElseThrow(),
                                    java.lang.foreign.FunctionDescriptor.of(I, P, P, P, I, I, I));
                            VK_INIT64_REASON = linker.downcallHandle(lib.find("vkchain64_fail_reason").orElseThrow(),
                                    java.lang.foreign.FunctionDescriptor.of(P));
                            VK_INIT64 = linker.downcallHandle(lib.find("vkchain64_init").orElseThrow(),
                                    java.lang.foreign.FunctionDescriptor.of(I, P));
                        } catch (Throwable t) {
                            VK_DISP64 = null; // lib 64 sem símbolos esperados
                        }
                    }
                    lib = java.lang.foreign.SymbolLookup.libraryLookup("libvkchain.so", arena);
                    VK_INIT = linker.downcallHandle(lib.find("vkchain_init").orElseThrow(),
                            java.lang.foreign.FunctionDescriptor.of(I, P));
                    VK_DISP = linker.downcallHandle(lib.find("vkchain_dispatch").orElseThrow(),
                            java.lang.foreign.FunctionDescriptor.of(I, P, P, P, I, I, I));
                    // reason: retorna char* — leitura manual até NUL
                    VK_REASON = linker.downcallHandle(lib.find("vkchain_fail_reason").orElseThrow(),
                            java.lang.foreign.FunctionDescriptor.of(P));
                    // spv path: env KOF_GPU_SPV ou default gpu/shaders/matmul.spv
                    String spv = System.getenv("KOF_GPU_SPV");
                    if (spv == null || spv.isEmpty()) spv = "gpu/shaders/matmul.spv";
                    // JDK 25+: heap segment rejeitado em downcall — aloca nativo
                    int rc;
                    try {
                        rc = (int) VK_INIT.invoke(nativeCstr(arena, spv));
                    } catch (Throwable t) {
                        VK_ERR = "init: " + t;
                        return false;
                    }
                    VK_ERR = readCString(VK_REASON);
                    if (VK_ERR == null) VK_ERR = "?";
                    return rc == 0;
                }

                private static String readCString(java.lang.invoke.MethodHandle fn) {
                    try {
                        var p0 = (java.lang.foreign.MemorySegment) fn.invoke();
                        var p = p0.reinterpret(Long.MAX_VALUE);
                        int n = 0;
                        while (p.get(java.lang.foreign.ValueLayout.JAVA_BYTE, n) != 0) n++;
                        byte[] out = new byte[n];
                        java.lang.foreign.MemorySegment.copy(p, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, out, 0, n);
                        return new String(out, java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Throwable t) {
                        return null;
                    }
                }
            }"""
;
    }
