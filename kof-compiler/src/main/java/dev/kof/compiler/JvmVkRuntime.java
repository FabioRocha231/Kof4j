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
                private static java.lang.invoke.MethodHandle VK_REASON;

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

                private static boolean kof_vk_init() {
                    var arena = java.lang.foreign.Arena.global();
                    var lib = java.lang.foreign.SymbolLookup.libraryLookup("libvkchain.so", arena);
                    var linker = java.lang.foreign.Linker.nativeLinker();
                    var P = java.lang.foreign.ValueLayout.ADDRESS;
                    var I = java.lang.foreign.ValueLayout.JAVA_INT;
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
                    // JDK 21: Arena não tem allocateFrom(String) (API JDK 22+);
                    // ofArray + copy mantém compatibilidade com release 21.
                    byte[] spvBytes = spv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    byte[] spvNul = new byte[spvBytes.length + 1];
                    System.arraycopy(spvBytes, 0, spvNul, 0, spvBytes.length);
                    var off = java.lang.foreign.MemorySegment.ofArray(spvNul);
                    int rc;
                    try {
                        rc = (int) VK_INIT.invoke(off);
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
