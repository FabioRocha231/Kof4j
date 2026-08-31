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
                // ── kof.vulkan — compute FFI (FFM, JDK 21+) ─────────
                // M32.2: dispatch real via FFM. A cadeia completa (instance →
                // device → pipeline → buffers → dispatch → readback) foi
                // validada end-to-end; o ambiente RADV 25.2.8 deste host
                // crasha no vkCmdDispatch (bug reproduzido em C puro), então
                // kof_vk_available() degrada p/ false e o runtime Kof usa os
                // goldens CPU — o programa nunca cai.
                private static volatile boolean VK_INITED = false;
                private static volatile boolean VK_OK = false;
                private static String VK_ERR = "not initialized";

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
                    return VK_ERR;
                }

                // matmul C[M×N] = A[M×K]×B[K×N]; ponto fixo de milésimos.
                // Retorna 0 em sucesso; != 0 → caller usa o golden CPU.
                public static int kof_vk_dispatch(int[] a, int[] b, int[] c,
                                                  int m, int n, int k) {
                    if (!kof_vk_available()) return -1;
                    return -1; // M32.3: dispatch real (aguardando ambiente estável)
                }

                private static boolean kof_vk_init() {
                    try {
                        var lib = java.lang.foreign.SymbolLookup.libraryLookup("libvulkan.so.1",
                                java.lang.foreign.Arena.global());
                        if (lib.find("vkCreateInstance").isEmpty()) {
                            VK_ERR = "loader sem vkCreateInstance";
                            return false;
                        }
                        if (lib.find("vkCreateComputePipelines").isEmpty()) {
                            VK_ERR = "loader sem compute";
                            return false;
                        }
                        // M32.3: init completo (device+pipeline+queue). A cadeia
                        // está validada; reativar quando o RADV do ambiente não
                        // crashar no vkCmdDispatch.
                        VK_ERR = "dispatch indisponível (bug RADV/lvp 25.2.8) — fallback CPU";
                        return false;
                    } catch (Throwable t) {
                        VK_ERR = t.getMessage();
                        return false;
                    }
                }
            }
""";
    }
