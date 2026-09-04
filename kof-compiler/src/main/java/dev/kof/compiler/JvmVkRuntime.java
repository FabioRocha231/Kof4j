package dev.kof.compiler;

/**
 * M36.4: Vulkan compute 100% FFM (java.lang.foreign) — substitui a
 * libvkchain64.c/vkchain.c. O text block {@link #VK_SOURCE} é injetado
 * dentro da classe KofRuntime gerada quando o programa usa kof.vk /
 * kof_mv64_* — o runtime fica autocontido (sem lib C, sem jar extra).
 *
 * Cadeia idêntica ao vkchain64.c: instance → physical 0 → device (queue
 * compute) → shader module (SPV) → desc layouts (3/5 SSBOs) → pipelines →
 * buffers host-visible coherent mapeados → descriptor sets → cmd buffer →
 * dispatch + fence. Structs Vulkan escritas byte a byte (offsets AMD64).
 *
 * Pipelines: matvec64 (W i64), matvecw32 (W i32 — metade do PCIe),
 * matvecsplit (wh/wl/xh/xl i32 → y i64 bit-exato com o CPU). SPVs: env
 * KOF_GPU_SPV64/_W32/_SPLIT, default gpu/shaders/*.spv. Pipeline ausente →
 * degrada (rc != 0) e o caller usa o golden CPU — nunca derruba o programa.
 *
 * dispatch32/dispatch64 (gpu.dispatchMatmul*) usam 1 WG por ELEMENTO c
 * (matmul.spv/matmul64.spv, push 12B {m,n,k}) — buffers a/b/c próprios.
 */
final class JvmVkRuntime {
    private JvmVkRuntime() {}

    static String source() {
        return VK_SOURCE;
    }

    private static final String VK_SOURCE = """
                // ── kof.vulkan — Vulkan compute 100% FFM (M36.4, sem libvkchain) ─
                // Toda a cadeia Vulkan vive aqui: structs byte a byte (offsets
                // AMD64 verificados contra vulkan_core.h), downcalls diretos da
                // libvulkan.so.1. Falha em qualquer passo → VK_OK=false e o
                // caller degrada p/ golden CPU. O programa nunca cai.
                private static volatile boolean VK_INITED = false;
                private static volatile boolean VK_OK = false;
                private static String VK_ERR = "not initialized";

                private static java.lang.invoke.MethodHandle vkCreateInstance;
                private static java.lang.invoke.MethodHandle vkEnumeratePhysicalDevices;
                private static java.lang.invoke.MethodHandle vkGetPhysicalDeviceQueueFamilyProperties;
                private static java.lang.invoke.MethodHandle vkCreateDevice;
                private static java.lang.invoke.MethodHandle vkGetDeviceQueue;
                private static java.lang.invoke.MethodHandle vkCreateShaderModule;
                private static java.lang.invoke.MethodHandle vkCreateDescriptorSetLayout;
                private static java.lang.invoke.MethodHandle vkCreatePipelineLayout;
                private static java.lang.invoke.MethodHandle vkCreateComputePipelines;
                private static java.lang.invoke.MethodHandle vkCreateDescriptorPool;
                private static java.lang.invoke.MethodHandle vkAllocateDescriptorSets;
                private static java.lang.invoke.MethodHandle vkCreateCommandPool;
                private static java.lang.invoke.MethodHandle vkAllocateCommandBuffers;
                private static java.lang.invoke.MethodHandle vkCreateFence;
                private static java.lang.invoke.MethodHandle vkCreateBuffer;
                private static java.lang.invoke.MethodHandle vkGetBufferMemoryRequirements;
                private static java.lang.invoke.MethodHandle vkGetPhysicalDeviceMemoryProperties;
                private static java.lang.invoke.MethodHandle vkAllocateMemory;
                private static java.lang.invoke.MethodHandle vkBindBufferMemory;
                private static java.lang.invoke.MethodHandle vkMapMemory;
                private static java.lang.invoke.MethodHandle vkUnmapMemory;
                private static java.lang.invoke.MethodHandle vkDestroyBuffer;
                private static java.lang.invoke.MethodHandle vkFreeMemory;
                private static java.lang.invoke.MethodHandle vkUpdateDescriptorSets;
                private static java.lang.invoke.MethodHandle vkBeginCommandBuffer;
                private static java.lang.invoke.MethodHandle vkCmdBindPipeline;
                private static java.lang.invoke.MethodHandle vkCmdBindDescriptorSets;
                private static java.lang.invoke.MethodHandle vkCmdPushConstants;
                private static java.lang.invoke.MethodHandle vkCmdDispatch;
                private static java.lang.invoke.MethodHandle vkEndCommandBuffer;
                private static java.lang.invoke.MethodHandle vkQueueSubmit;
                private static java.lang.invoke.MethodHandle vkWaitForFences;
                private static java.lang.invoke.MethodHandle vkResetFences;

                private static java.lang.foreign.MemorySegment VK_INST;
                private static java.lang.foreign.MemorySegment VK_PHYS;
                private static java.lang.foreign.MemorySegment VK_DEV;
                private static java.lang.foreign.MemorySegment VK_QUEUE;
                private static java.lang.foreign.MemorySegment VK_CMD;
                private static java.lang.foreign.MemorySegment VK_FENCE;
                private static java.lang.foreign.MemorySegment VK_PL3;
                private static java.lang.foreign.MemorySegment VK_PL5;
                private static java.lang.foreign.MemorySegment VK_PL12;
                private static java.lang.foreign.MemorySegment VK_PIPE64;
                private static java.lang.foreign.MemorySegment VK_PIPE32;
                private static java.lang.foreign.MemorySegment VK_PIPESPL;
                private static java.lang.foreign.MemorySegment VK_PIPEMM;
                private static java.lang.foreign.MemorySegment VK_PIPEMM64;
                private static java.lang.foreign.MemorySegment VK_DSET3;
                private static java.lang.foreign.MemorySegment VK_DSET5;
                private static java.lang.foreign.MemorySegment VK_DSET12;
                private static java.lang.foreign.Arena VK_ARENA;

                // slots mutáveis: buf[0], mem[1], map[2] + cap
                private static final java.lang.foreign.MemorySegment[] S_X = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_Y = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_W = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_X32 = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_Y32 = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_XH = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_XL = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_A = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_B = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_C = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_A64 = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_B64 = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_C64 = new java.lang.foreign.MemorySegment[3];
                private static final long[] C_X = new long[1];
                private static final long[] C_Y = new long[1];
                private static final long[] C_W = new long[1];
                private static final long[] C_X32 = new long[1];
                private static final long[] C_Y32 = new long[1];
                private static final long[] C_XH = new long[1];
                private static final long[] C_XL = new long[1];
                private static final long[] C_A = new long[1];
                private static final long[] C_B = new long[1];
                private static final long[] C_C = new long[1];
                private static final long[] C_A64 = new long[1];
                private static final long[] C_B64 = new long[1];
                private static final long[] C_C64 = new long[1];

                // W residente por id: [id][0]=buf [id][1]=mem [id][2]=map
                private static final int VK_WMAX = 192;
                private static final java.lang.foreign.MemorySegment[][] W64 = new java.lang.foreign.MemorySegment[VK_WMAX][3];
                private static final java.lang.foreign.MemorySegment[][] W32 = new java.lang.foreign.MemorySegment[VK_WMAX][3];
                private static final java.lang.foreign.MemorySegment[][] WH = new java.lang.foreign.MemorySegment[VK_WMAX][3];
                private static final java.lang.foreign.MemorySegment[][] WL = new java.lang.foreign.MemorySegment[VK_WMAX][3];
                private static final long[][] W64CAP = new long[VK_WMAX][1];
                private static final long[][] W32CAP = new long[VK_WMAX][1];
                private static final long[][] WHCAP = new long[VK_WMAX][1];
                private static final long[][] WLCAP = new long[VK_WMAX][1];

                private static int VK_CURM;
                private static int VK_CURK;

                private static java.lang.foreign.MemorySegment vkAlloc(long bytes) {
                    return VK_ARENA.allocate(bytes);
                }

                private static void putI(java.lang.foreign.MemorySegment s, long off, int v) {
                    s.set(java.lang.foreign.ValueLayout.JAVA_INT, off, v);
                }

                private static void putL(java.lang.foreign.MemorySegment s, long off, long v) {
                    s.set(java.lang.foreign.ValueLayout.JAVA_LONG, off, v);
                }

                private static void putP(java.lang.foreign.MemorySegment s, long off,
                        java.lang.foreign.MemorySegment p) {
                    s.set(java.lang.foreign.ValueLayout.ADDRESS, off, p);
                }

                private static int getI(java.lang.foreign.MemorySegment s, long off) {
                    return s.get(java.lang.foreign.ValueLayout.JAVA_INT, off);
                }

                private static long getL(java.lang.foreign.MemorySegment s, long off) {
                    return s.get(java.lang.foreign.ValueLayout.JAVA_LONG, off);
                }

                private static java.lang.foreign.MemorySegment getP(java.lang.foreign.MemorySegment s, long off) {
                    return s.get(java.lang.foreign.ValueLayout.ADDRESS, off);
                }

                private static java.lang.foreign.MemorySegment vkNull() {
                    return java.lang.foreign.MemorySegment.NULL;
                }

                private static java.lang.foreign.MemorySegment vkOut() {
                    return vkAlloc(8);
                }

                private static java.lang.foreign.MemorySegment vkRes(java.lang.foreign.MemorySegment out) {
                    return out.get(java.lang.foreign.ValueLayout.ADDRESS, 0);
                }

                private static int vk(int rc, String what) throws Throwable {
                    if (rc != 0) {
                        VK_ERR = what + " (rc=" + (-rc) + ")";
                        throw new RuntimeException(VK_ERR);
                    }
                    return rc;
                }

                private static java.lang.invoke.MethodHandle vkFn(
                        java.lang.foreign.SymbolLookup lib, String name,
                        java.lang.foreign.FunctionDescriptor desc) throws Throwable {
                    var linker = java.lang.foreign.Linker.nativeLinker();
                    return linker.downcallHandle(
                            lib.find(name).orElseThrow(
                                    () -> new RuntimeException("simbolo ausente: " + name)),
                            desc);
                }

                public static boolean kof_vk_available() {
                    if (!VK_INITED) {
                        try {
                            VK_OK = vkInitAll();
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

                private static boolean vkInitAll() {
                    if (VK_DEV != null) return true;
                    try {
                        VK_ARENA = java.lang.foreign.Arena.ofAuto();
                        var lib = java.lang.foreign.SymbolLookup.libraryLookup(
                                "libvulkan.so.1", VK_ARENA);
                        var I = java.lang.foreign.ValueLayout.JAVA_INT;
                        var L = java.lang.foreign.ValueLayout.JAVA_LONG;
                        var P = java.lang.foreign.ValueLayout.ADDRESS;
                        var F = java.lang.foreign.FunctionDescriptor;
                        vkCreateInstance = vkFn(lib, "vkCreateInstance", F.of(I, P, P, P));
                        vkEnumeratePhysicalDevices = vkFn(lib, "vkEnumeratePhysicalDevices",
                                F.of(I, P, P, P));
                        vkGetPhysicalDeviceQueueFamilyProperties = vkFn(lib,
                                "vkGetPhysicalDeviceQueueFamilyProperties", F.ofVoid(P, P, P));
                        vkCreateDevice = vkFn(lib, "vkCreateDevice", F.of(I, P, P, P, P));
                        vkGetDeviceQueue = vkFn(lib, "vkGetDeviceQueue", F.ofVoid(P, I, I, P));
                        vkCreateShaderModule = vkFn(lib, "vkCreateShaderModule", F.of(I, P, P, P));
                        vkCreateDescriptorSetLayout = vkFn(lib, "vkCreateDescriptorSetLayout",
                                F.of(I, P, P, P));
                        vkCreatePipelineLayout = vkFn(lib, "vkCreatePipelineLayout",
                                F.of(I, P, P, P));
                        vkCreateComputePipelines = vkFn(lib, "vkCreateComputePipelines",
                                F.of(I, P, P, I, P, P, P));
                        vkCreateDescriptorPool = vkFn(lib, "vkCreateDescriptorPool",
                                F.of(I, P, P, P));
                        vkAllocateDescriptorSets = vkFn(lib, "vkAllocateDescriptorSets",
                                F.of(I, P, P));
                        vkCreateCommandPool = vkFn(lib, "vkCreateCommandPool", F.of(I, P, P, P));
                        vkAllocateCommandBuffers = vkFn(lib, "vkAllocateCommandBuffers",
                                F.of(I, P, P));
                        vkCreateFence = vkFn(lib, "vkCreateFence", F.of(I, P, P, P));
                        vkCreateBuffer = vkFn(lib, "vkCreateBuffer", F.of(I, P, P, P));
                        vkGetBufferMemoryRequirements = vkFn(lib,
                                "vkGetBufferMemoryRequirements", F.ofVoid(P, P));
                        vkGetPhysicalDeviceMemoryProperties = vkFn(lib,
                                "vkGetPhysicalDeviceMemoryProperties", F.ofVoid(P, P));
                        vkAllocateMemory = vkFn(lib, "vkAllocateMemory", F.of(I, P, P, P));
                        vkBindBufferMemory = vkFn(lib, "vkBindBufferMemory", F.of(I, P, P, L, P));
                        vkMapMemory = vkFn(lib, "vkMapMemory", F.of(I, P, L, L, I, P));
                        vkUnmapMemory = vkFn(lib, "vkUnmapMemory", F.ofVoid(P, P));
                        vkDestroyBuffer = vkFn(lib, "vkDestroyBuffer", F.ofVoid(P, P, P));
                        vkFreeMemory = vkFn(lib, "vkFreeMemory", F.ofVoid(P, P, P));
                        vkUpdateDescriptorSets = vkFn(lib, "vkUpdateDescriptorSets",
                                F.ofVoid(P, I, P, I, P));
                        vkBeginCommandBuffer = vkFn(lib, "vkBeginCommandBuffer", F.of(I, P, P));
                        vkCmdBindPipeline = vkFn(lib, "vkCmdBindPipeline", F.ofVoid(P, I, P));
                        vkCmdBindDescriptorSets = vkFn(lib, "vkCmdBindDescriptorSets",
                                F.ofVoid(P, I, P, I, I, P, I, P));
                        vkCmdPushConstants = vkFn(lib, "vkCmdPushConstants",
                                F.ofVoid(P, P, I, I, I, P));
                        vkCmdDispatch = vkFn(lib, "vkCmdDispatch", F.ofVoid(P, I, I, I));
                        vkEndCommandBuffer = vkFn(lib, "vkEndCommandBuffer", F.of(I, P));
                        vkQueueSubmit = vkFn(lib, "vkQueueSubmit", F.of(I, P, I, P, P));
                        vkWaitForFences = vkFn(lib, "vkWaitForFences", F.of(I, P, I, I, L));
                        vkResetFences = vkFn(lib, "vkResetFences", F.of(I, P, I, P));
                        vkInitChain();
                        return true;
                    } catch (Throwable t) {
                        VK_ERR = t.getClass().getSimpleName() + ": " + t.getMessage();
                        return false;
                    }
                }

                private static String envSpvErr;

                private static String envSpv(String env, String fallback) {
                    String v = System.getenv(env);
                    if (v != null && !v.isBlank()) {
                        if (java.nio.file.Files.isRegularFile(java.nio.file.Path.of(v))) {
                            return v;
                        }
                        envSpvErr = env + " aponta p/ arquivo ausente: " + v;
                        return null;
                    }
                    if (java.nio.file.Files.isRegularFile(java.nio.file.Path.of(fallback))) {
                        return fallback;
                    }
                    envSpvErr = "sem " + env + " e sem " + fallback;
                    return null;
                }

                private static String envSpvOpt(String env, String fallback) {
                    String v = System.getenv(env);
                    if (v != null && !v.isBlank()
                            && java.nio.file.Files.isRegularFile(java.nio.file.Path.of(v))) {
                        return v;
                    }
                    return java.nio.file.Files.isRegularFile(java.nio.file.Path.of(fallback))
                            ? fallback : null;
                }

                private static void vkInitChain() throws Throwable {
                    var a = VK_ARENA;

                    // VkApplicationInfo (48B AMD64): pApplicationName@16,
                    // applicationVersion@24, pEngineName@32,
                    // engineVersion@40, apiVersion@44 = VK_API_VERSION_1_3
                    var ai = vkAlloc(48);
                    putI(ai, 0, 0);
                    putI(ai, 44, 0x00403000);

                    // VkInstanceCreateInfo (64B): pApplicationInfo@24
                    var ici = vkAlloc(64);
                    putI(ici, 0, 1);
                    putP(ici, 24, ai);
                    var instOut = vkOut();
                    vk((int) vkCreateInstance.invoke(ici, vkNull(), instOut), "instance");
                    VK_INST = vkRes(instOut);

                    // physical device 0
                    var nOut = vkAlloc(8);
                    putI(nOut, 0, 0);
                    vk((int) vkEnumeratePhysicalDevices.invoke(VK_INST, nOut, vkNull()), "enum0");
                    int n = getI(nOut, 0);
                    if (n == 0) {
                        VK_ERR = "nenhum physical device";
                        throw new RuntimeException(VK_ERR);
                    }
                    var physv = a.allocate(8);
                    vk((int) vkEnumeratePhysicalDevices.invoke(VK_INST, nOut, physv), "enum");
                    VK_PHYS = getP(physv, 0);

                    // queue family com QUEUE_COMPUTE_BIT (0x2)
                    var qnOut = vkAlloc(8);
                    putI(qnOut, 0, 0);
                    vkGetPhysicalDeviceQueueFamilyProperties.invoke(VK_PHYS, qnOut, vkNull());
                    int qn = Math.min(getI(qnOut, 0), 8);
                    var qf = a.allocate(8L * qn);   // VkQueueFamilyProperties 8B
                    vkGetPhysicalDeviceQueueFamilyProperties.invoke(VK_PHYS, qnOut, qf);
                    int qfam = -1;
                    for (int i = 0; i < qn; i++) {
                        if ((getI(qf, (long) i * 8) & 0x2) != 0) {
                            qfam = i;
                            break;
                        }
                    }
                    if (qfam < 0) {
                        VK_ERR = "sem compute queue";
                        throw new RuntimeException(VK_ERR);
                    }

                    // VkDeviceQueueCreateInfo (40B): queueFamilyIndex@20,
                    // queueCount@24, pQueuePriorities@32
                    var prio = a.allocate(4);
                    prio.set(java.lang.foreign.ValueLayout.JAVA_FLOAT, 0, 1.0f);
                    var qci = vkAlloc(40);
                    putI(qci, 0, 2);
                    putI(qci, 20, qfam);
                    putI(qci, 24, 1);
                    putP(qci, 32, prio);

                    // VkDeviceCreateInfo (72B): queueCreateInfoCount@20,
                    // pQueueCreateInfos@24, pEnabledFeatures@64 NULL
                    var dci = vkAlloc(72);
                    putI(dci, 0, 3);
                    putI(dci, 20, 1);
                    putP(dci, 24, qci);
                    putL(dci, 64, 0);
                    var devOut = vkOut();
                    vk((int) vkCreateDevice.invoke(VK_PHYS, dci, vkNull(), devOut), "device");
                    VK_DEV = vkRes(devOut);
                    var qOut = vkOut();
                    vkGetDeviceQueue.invoke(VK_DEV, qfam, 0, qOut);
                    VK_QUEUE = vkRes(qOut);

                    // layouts + pipelines (SPVs default gpu/shaders/*.spv ou env)
                    VK_PL3 = vkMakeLayout(3);
                    VK_PL5 = vkMakeLayout(5);
                    VK_PL12 = vkMakeLayoutPush(3, 12);   // matmul: push {m,n,k} 12B
                    String spv64 = envSpv("KOF_GPU_SPV64", "gpu/shaders/matvec64.spv");
                    VK_PIPE64 = (spv64 != null) ? vkMakePipe(spv64, VK_PL3, 24) : null;
                    if (VK_PIPE64 == null) {
                        VK_ERR = "SPIR-V matvec64: " + envSpvErr;
                        return;
                    }
                    VK_DSET3 = vkMakeSet(3, VK_PL3);
                    String spvS = envSpvOpt("KOF_GPU_SPV64_SPLIT", "gpu/shaders/matvecsplit.spv");
                    if (spvS != null) {
                        VK_PIPESPL = vkMakePipe(spvS, VK_PL5, 24);
                        if (VK_PIPESPL != null) {
                            VK_DSET5 = vkMakeSet(5, VK_PL5);
                        }
                    }
                    String spv32 = envSpvOpt("KOF_GPU_SPV64_W32", "gpu/shaders/matvecw32.spv");
                    if (spv32 != null) {
                        VK_PIPE32 = vkMakePipe(spv32, VK_PL3, 24);
                    }
                    String spvMM = envSpvOpt("KOF_GPU_SPV", "gpu/shaders/matmul.spv");
                    if (spvMM != null) {
                        VK_PIPEMM = vkMakePipe(spvMM, VK_PL12, 12);
                        if (VK_PIPEMM != null) {
                            VK_DSET12 = vkMakeSet(3, VK_PL12);
                        }
                    }
                    // matmul64: KOF_GPU_SPV64_MM, senão KOF_GPU_SPV64 (o
                    // matmul64 e o matvec64 vivem no mesmo dir; o desenho
                    // antigo usava a env 64 p/ ambos)
                    String spvMM64 = envSpvOpt("KOF_GPU_SPV64_MM",
                            envSpvOpt("KOF_GPU_SPV64", "gpu/shaders/matmul64.spv"));
                    if (spvMM64 != null) {
                        VK_PIPEMM64 = vkMakePipe(spvMM64, VK_PL12, 12);
                    }

                    // VkCommandPoolCreateInfo (24B): queueFamilyIndex@20
                    var cpi = vkAlloc(24);
                    putI(cpi, 0, 39);
                    putI(cpi, 20, qfam);
                    var cpOut = vkOut();
                    vk((int) vkCreateCommandPool.invoke(VK_DEV, cpi, vkNull(), cpOut), "cmd pool");
                    var cpool = vkRes(cpOut);

                    // VkCommandBufferAllocateInfo (32B): commandPool@16,
                    // level@24 = PRIMARY(0), commandBufferCount@28
                    var cbai = vkAlloc(32);
                    putI(cbai, 0, 40);
                    putP(cbai, 16, cpool);
                    putI(cbai, 24, 0);
                    putI(cbai, 28, 1);
                    var cbOut = vkOut();
                    vk((int) vkAllocateCommandBuffers.invoke(VK_DEV, cbai, cbOut), "cmd buf");
                    VK_CMD = vkRes(cbOut);

                    // VkFenceCreateInfo (24B)
                    var fci = vkAlloc(24);
                    putI(fci, 0, 8);
                    var fOut = vkOut();
                    vk((int) vkCreateFence.invoke(VK_DEV, fci, vkNull(), fOut), "fence");
                    VK_FENCE = vkRes(fOut);
                    VK_ERR = "ok";
                }

                // VkDescriptorSetLayoutBinding (24B): binding@0,
                // descriptorType@4 = STORAGE_BUFFER(7), descriptorCount@8,
                // stageFlags@12 = COMPUTE(0x20), pImmutableSamplers@16
                private static java.lang.foreign.MemorySegment vkMakeLayout(int nbinds)
                        throws Throwable {
                    return vkMakeLayoutPush(nbinds, 24);
                }

                // VkPipelineLayoutCreateInfo (48B): flags@16, setLayoutCount@20,
                // pSetLayouts@24, pushConstantRangeCount@32, pPushConstantRanges@40
                private static java.lang.foreign.MemorySegment vkMakeLayoutPush(int nbinds,
                        int pushSize) throws Throwable {
                    var binds = vkAlloc(24L * nbinds);
                    for (int i = 0; i < nbinds; i++) {
                        long o = (long) i * 24;
                        putI(binds, o, i);
                        putI(binds, o + 4, 7);
                        putI(binds, o + 8, 1);
                        putI(binds, o + 12, 0x20);
                    }
                    var li = vkAlloc(32);
                    putI(li, 0, 24);
                    putI(li, 20, nbinds);
                    putP(li, 24, binds);
                    var lo = vkOut();
                    vk((int) vkCreateDescriptorSetLayout.invoke(VK_DEV, li, vkNull(), lo),
                            "desc layout");
                    var dsl = vkRes(lo);

                    var pcr = vkAlloc(12);      // VkPushConstantRange: offset@4, size@8
                    putI(pcr, 4, 0);
                    putI(pcr, 8, pushSize);
                    var pli = vkAlloc(48);
                    putI(pli, 0, 26);
                    putI(pli, 20, 1);
                    putP(pli, 24, dsl);
                    putI(pli, 32, 1);
                    putP(pli, 40, pcr);
                    var plo = vkOut();
                    vk((int) vkCreatePipelineLayout.invoke(VK_DEV, pli, vkNull(), plo),
                            "pipeline layout");
                    return vkRes(plo);
                }

                // VkPipelineShaderStageCreateInfo (48B): flags@16,
                // stage@20 = COMPUTE(5), module@24, pName@32 — INLINE no
                // ComputePipelineInfo @24 (lição do debug: sem array separado).
                private static java.lang.foreign.MemorySegment vkMakePipe(String spvPath,
                        java.lang.foreign.MemorySegment layout, int pushSize) throws Throwable {
                    byte[] spv = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(spvPath));
                    // VkShaderModuleCreateInfo (40B): flags@16,
                    // codeSize@24 (size_t), pCode@32
                    var smci = vkAlloc(40);
                    putI(smci, 0, 21);
                    putL(smci, 24, spv.length);
                    var code = vkAlloc(spv.length);
                    java.lang.foreign.MemorySegment.copy(
                            java.lang.foreign.MemorySegment.ofArray(spv), 0, code, 0, spv.length);
                    putP(smci, 32, code);
                    var smOut = vkOut();
                    vk((int) vkCreateShaderModule.invoke(VK_DEV, smci, vkNull(), smOut),
                            "shader module");
                    var sm = vkRes(smOut);

                    var pcr = vkAlloc(12);
                    putI(pcr, 4, 0);
                    putI(pcr, 8, pushSize);
                    var pli = vkAlloc(48);
                    putI(pli, 0, 26);
                    putI(pli, 20, 1);
                    putP(pli, 24, layout);
                    putI(pli, 32, 1);
                    putP(pli, 40, pcr);
                    var plo = vkOut();
                    vk((int) vkCreatePipelineLayout.invoke(VK_DEV, pli, vkNull(), plo),
                            "pipeline layout");
                    var pl = vkRes(plo);

                    var stage = vkAlloc(48);
                    putI(stage, 20, 5);
                    putP(stage, 24, sm);
                    putP(stage, 32, vkCstr("main"));
                    var cpci = vkAlloc(96);
                    putI(cpci, 0, 25);
                    java.lang.foreign.MemorySegment.copy(stage, 0, cpci, 24, 48);
                    putP(cpci, 72, pl);
                    var po = vkOut();
                    vk((int) vkCreateComputePipelines.invoke(VK_DEV, vkNull(), cpci, 1,
                            vkNull(), po), "pipeline");
                    return vkRes(po);
                }

                private static java.lang.foreign.MemorySegment vkCstr(String s) {
                    byte[] b = (s + "\\0").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    var seg = VK_ARENA.allocate(b.length);
                    java.lang.foreign.MemorySegment.copy(
                            java.lang.foreign.MemorySegment.ofArray(b), 0, seg, 0, b.length);
                    return seg;
                }

                // VkDescriptorPoolSize (8B): type@0 = STORAGE_BUFFER(7), count@4.
                // VkDescriptorPoolCreateInfo (40B): flags@16, maxSets@20,
                // poolSizeCount@24, pPoolSizes@32. VkDescriptorSetAllocateInfo
                // (40B): descriptorPool@16, descriptorSetCount@24, pSetLayouts@32.
                private static java.lang.foreign.MemorySegment vkMakeSet(int nbinds,
                        java.lang.foreign.MemorySegment layout) throws Throwable {
                    var psize = vkAlloc(8);
                    putI(psize, 0, 7);
                    putI(psize, 4, nbinds);
                    var dpi = vkAlloc(40);
                    putI(dpi, 0, 22);
                    putI(dpi, 20, nbinds);
                    putI(dpi, 24, 1);
                    putP(dpi, 32, psize);
                    var dpo = vkOut();
                    vk((int) vkCreateDescriptorPool.invoke(VK_DEV, dpi, vkNull(), dpo),
                            "desc pool");
                    var pool = vkRes(dpo);

                    var dsai = vkAlloc(40);
                    putI(dsai, 0, 32);
                    putP(dsai, 16, pool);
                    putI(dsai, 24, 1);
                    putP(dsai, 32, layout);
                    var dso = vkAlloc(8);
                    vk((int) vkAllocateDescriptorSets.invoke(VK_DEV, dsai, dso), "desc set");
                    return getP(dso, 0);
                }

                // buffer host-visible|coherent: create → requirements → mem type
                // → alloc → bind → map. slot = {buf, mem, map}
                private static void vkHostBuffer(long bytes, java.lang.foreign.MemorySegment[] slot)
                        throws Throwable {
                    // VkBufferCreateInfo (56B): flags@16, size@24 (DeviceSize),
                    // usage@32 = STORAGE_BUFFER(0x80), sharingMode@36 = EXCLUSIVE(0)
                    var bci = vkAlloc(56);
                    putI(bci, 0, 15);
                    putL(bci, 24, bytes);
                    putI(bci, 32, 0x80);
                    putI(bci, 36, 0);
                    var bo = vkOut();
                    vk((int) vkCreateBuffer.invoke(VK_DEV, bci, vkNull(), bo), "buffer");
                    slot[0] = vkRes(bo);

                    var mr = vkAlloc(24);       // VkMemoryRequirements: size@0,
                                                // memoryTypeBits@16
                    vkGetBufferMemoryRequirements.invoke(VK_DEV, slot[0], mr);
                    long size = getL(mr, 0);
                    int memTypeBits = getI(mr, 16);

                    var pdmp = vkAlloc(520);    // VkPhysicalDeviceMemoryProperties:
                                                // memoryTypeCount@0, types@4 (32×8B:
                                                // propertyFlags@4 de cada)
                    vkGetPhysicalDeviceMemoryProperties.invoke(VK_PHYS, pdmp);
                    int nTypes = Math.min(getI(pdmp, 0), 32);
                    int memIdx = -1;
                    for (int t = 0; t < nTypes; t++) {
                        long o = 4L + (long) t * 8;
                        int props = getI(pdmp, o + 4);
                        if ((memTypeBits & (1 << t)) != 0
                                && (props & 0x6) == 0x6) {  // HOST_VISIBLE|COHERENT
                            memIdx = t;
                            break;
                        }
                    }
                    if (memIdx < 0) {
                        VK_ERR = "sem mem host-visible";
                        throw new RuntimeException(VK_ERR);
                    }

                    var mai = vkAlloc(32);
                    putI(mai, 0, 5);
                    putL(mai, 16, size);
                    putI(mai, 24, memIdx);
                    var mo = vkOut();
                    vk((int) vkAllocateMemory.invoke(VK_DEV, mai, vkNull(), mo), "alloc mem");
                    slot[1] = vkRes(mo);
                    vk((int) vkBindBufferMemory.invoke(VK_DEV, slot[0], slot[1], 0L, vkNull()),
                            "bind mem");
                    var mpOut = vkOut();
                    vk((int) vkMapMemory.invoke(VK_DEV, slot[1], 0L, size, 0, mpOut), "map");
                    slot[2] = vkRes(mpOut);
                }

                private static void vkDrop(java.lang.foreign.MemorySegment[] slot) throws Throwable {
                    if (slot[1] != null) {
                        vkUnmapMemory.invoke(VK_DEV, slot[1]);
                        vkDestroyBuffer.invoke(VK_DEV, slot[0], vkNull());
                        vkFreeMemory.invoke(VK_DEV, slot[1], vkNull());
                        slot[0] = null;
                        slot[1] = null;
                        slot[2] = null;
                    }
                }

                // (re)dimensiona buffer com capacidade: idempotente
                private static void vkGrow(java.lang.foreign.MemorySegment[] slot, long[] cap,
                        long bytes) throws Throwable {
                    if (slot[2] != null && cap[0] >= bytes) return;
                    vkDrop(slot);
                    vkHostBuffer(bytes, slot);
                    cap[0] = bytes;
                }

                // slot W residente por id: aloca se cresceu; devolve o mapped
                private static java.lang.foreign.MemorySegment vkSlot(
                        java.lang.foreign.MemorySegment[] slot, long[] cap, long bytes)
                        throws Throwable {
                    if (slot[2] != null && cap[0] >= bytes) return slot[2];
                    vkDrop(slot);
                    vkHostBuffer(bytes, slot);
                    cap[0] = bytes;
                    return slot[2];
                }

                private static void putLongs(java.lang.foreign.MemorySegment map,
                        long[] v, int n) {
                    for (int i = 0; i < n; i++) {
                        map.setAtIndex(java.lang.foreign.ValueLayout.JAVA_LONG, i, v[i]);
                    }
                }

                private static void getLongs(java.lang.foreign.MemorySegment map,
                        long[] out, int n) {
                    for (int i = 0; i < n; i++) {
                        out[i] = map.getAtIndex(java.lang.foreign.ValueLayout.JAVA_LONG, i);
                    }
                }

                private static void putInts(java.lang.foreign.MemorySegment map,
                        int[] v, int n) {
                    for (int i = 0; i < n; i++) {
                        map.setAtIndex(java.lang.foreign.ValueLayout.JAVA_INT, i, v[i]);
                    }
                }

                // VkDescriptorBufferInfo (24B): buffer@0, offset@8, range@16 =
                // WHOLE_SIZE. VkWriteDescriptorSet (64B): dstSet@8,
                // dstBinding@16, descriptorCount@20, descriptorType@24,
                // pBufferInfo@48.
                // VkDescriptorBufferInfo (24B): buffer@0, offset@8, range@16 =
                // WHOLE_SIZE. VkWriteDescriptorSet (64B): dstSet@16,
                // dstBinding@24, dstArrayElement@28, descriptorCount@32,
                // descriptorType@36, pImageInfo@40, pBufferInfo@48.
                private static void vkBindBuf(java.lang.foreign.MemorySegment dset,
                        int binding, java.lang.foreign.MemorySegment buf) throws Throwable {
                    var dbi = vkAlloc(24);
                    putP(dbi, 0, buf);
                    putL(dbi, 8, 0L);
                    putL(dbi, 16, -1L);
                    var w = vkAlloc(64);
                    putI(w, 0, 56);
                    putP(w, 16, dset);
                    putI(w, 24, binding);
                    putI(w, 28, 0);
                    putI(w, 32, 1);
                    putI(w, 36, 7);
                    putP(w, 48, dbi);
                    vkUpdateDescriptorSets.invoke(VK_DEV, 1, w, 0, vkNull());
                }

                // dispatch matvec (push 24B {m:k32, k:k32, divId, pad, div:i64},
                // layout do matvec64.comp/w32/split), fence + wait
                private static void vkRunMV(java.lang.foreign.MemorySegment pipe,
                        java.lang.foreign.MemorySegment dset, boolean five,
                        java.lang.foreign.MemorySegment[] bufs, int m, int k, long div)
                        throws Throwable {
                    var layout = five ? VK_PL5 : VK_PL3;
                    for (int b = 0; b < bufs.length; b++) {
                        vkBindBuf(dset, b, bufs[b]);
                    }
                    vkSubmit(pipe, layout, dset, m, k, div, 24);
                }

                // dispatch matmul (push 12B {m,n,k}; 1 WG por ELEMENTO c)
                private static void vkRunMM(java.lang.foreign.MemorySegment pipe,
                        java.lang.foreign.MemorySegment dset, int m, int n, int k)
                        throws Throwable {
                    vkBindBuf(dset, 0, S_A[0]);
                    vkBindBuf(dset, 1, S_B[0]);
                    vkBindBuf(dset, 2, S_C[0]);
                    vkSubmitMM(pipe, VK_PL12, dset, m, n, k);
                }

                private static void vkSubmit(java.lang.foreign.MemorySegment pipe,
                        java.lang.foreign.MemorySegment layout,
                        java.lang.foreign.MemorySegment dset, int m, int k, long div,
                        int pushSize) throws Throwable {
                    // VkCommandBufferBeginInfo (32B): flags@16 =
                    // USAGE_ONE_TIME_SUBMIT(1), pInheritanceInfo@24
                    var bbi = vkAlloc(32);
                    putI(bbi, 0, 42);
                    putI(bbi, 16, 1);
                    vk((int) vkBeginCommandBuffer.invoke(VK_CMD, bbi), "begin");
                    vkCmdBindPipeline.invoke(VK_CMD, 6, pipe);
                    var dsets = vkAlloc(8);
                    putP(dsets, 0, dset);
                    vkCmdBindDescriptorSets.invoke(VK_CMD, 6, layout, 0, 1, dsets, 0, vkNull());
                    int divId = (div == 1_000_000_000L) ? 0 : ((div == 1_000_000L) ? 1 : 2);
                    var push = vkAlloc(24);
                    putI(push, 0, m);
                    putI(push, 4, k);
                    putI(push, 8, divId);
                    putI(push, 12, 0);
                    putL(push, 16, div);
                    vkCmdPushConstants.invoke(VK_CMD, layout, 0x20, 0, pushSize, push);
                    vkCmdDispatch.invoke(VK_CMD, m, 1, 1);
                    vkEndAndWait();
                }

                private static void vkSubmitMM(java.lang.foreign.MemorySegment pipe,
                        java.lang.foreign.MemorySegment layout,
                        java.lang.foreign.MemorySegment dset, int m, int n, int k)
                        throws Throwable {
                    var bbi = vkAlloc(32);
                    putI(bbi, 0, 42);
                    putI(bbi, 16, 1);
                    vk((int) vkBeginCommandBuffer.invoke(VK_CMD, bbi), "begin");
                    vkCmdBindPipeline.invoke(VK_CMD, 6, pipe);
                    var dsets = vkAlloc(8);
                    putP(dsets, 0, dset);
                    vkCmdBindDescriptorSets.invoke(VK_CMD, 6, layout, 0, 1, dsets, 0, vkNull());
                    var push = vkAlloc(12);
                    putI(push, 0, m);
                    putI(push, 4, n);
                    putI(push, 8, k);
                    vkCmdPushConstants.invoke(VK_CMD, layout, 0x20, 0, 12, push);
                    vkCmdDispatch.invoke(VK_CMD, m * n, 1, 1);
                    vkEndAndWait();
                }

                private static void vkEndAndWait() throws Throwable {
                    vk((int) vkEndCommandBuffer.invoke(VK_CMD), "end");
                    // VkSubmitInfo (72B): waitSemaphoreCount@16,
                    // pWaitSemaphores@24, pWaitDstStageMask@32,
                    // commandBufferCount@40, pCommandBuffers@48,
                    // signalSemaphoreCount@56, pSignalSemaphores@64
                    var si = vkAlloc(72);
                    putI(si, 0, 4);
                    putI(si, 40, 1);
                    putP(si, 48, VK_CMD);
                    vk((int) vkQueueSubmit.invoke(VK_QUEUE, 1, si, VK_FENCE), "submit");
                    vk((int) vkWaitForFences.invoke(VK_DEV, 1, VK_FENCE, 1, 5_000_000_000L),
                            "wait fence");
                    vkResetFences.invoke(VK_DEV, 1, VK_FENCE);
                }

                // ── matvec residente (contrato do vkchain64.c) ────────────────

                public static int kof_mv64_set_shape(int m, int k) {
                    if (!kof_vk_available() || VK_PIPE64 == null) return -1;
                    try {
                        vkGrow(S_X, C_X, (long) k * 8);
                        vkGrow(S_Y, C_Y, (long) m * 8);
                        VK_CURM = m;
                        VK_CURK = k;
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_load_w(long[] w, int m, int k) {
                    if (!kof_vk_available() || VK_PIPE64 == null) return -1;
                    try {
                        var map = vkSlot(S_W, C_W, (long) m * k * 8);
                        putLongs(map, w, m * k);
                        VK_CURM = m;
                        VK_CURK = k;
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_matvec(long[] x, long[] y, int m, int k) {
                    if (!kof_vk_available() || VK_PIPE64 == null || S_W[2] == null) return -1;
                    if (VK_CURM != m || VK_CURK != k) return -2;
                    try {
                        vkGrow(S_X, C_X, (long) k * 8);
                        vkGrow(S_Y, C_Y, (long) m * 8);
                        putLongs(S_X[2], x, k);
                        vkRunMV(VK_PIPE64, VK_DSET3, false,
                                new java.lang.foreign.MemorySegment[]{S_W[0], S_X[0], S_Y[0]},
                                m, k, 0L);
                        getLongs(S_Y[2], y, m);
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_wput(int id, long[] w, int m, int k) {
                    if (!kof_vk_available() || VK_PIPE64 == null) return -1;
                    if (id < 0 || id >= VK_WMAX || (long) m * k <= 0) return -2;
                    try {
                        var map = vkSlot(W64[id], W64CAP[id], (long) m * k * 8);
                        putLongs(map, w, m * k);
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_wrun(int id, long[] x, long[] y, int m, int k,
                        long div) {
                    if (!kof_vk_available() || VK_PIPE64 == null) return -1;
                    if (id < 0 || id >= VK_WMAX || W64[id][2] == null
                            || W64CAP[id][0] < (long) m * k * 8) return -2;
                    try {
                        vkGrow(S_X, C_X, (long) k * 8);
                        vkGrow(S_Y, C_Y, (long) m * 8);
                        putLongs(S_X[2], x, k);
                        vkRunMV(VK_PIPE64, VK_DSET3, false,
                                new java.lang.foreign.MemorySegment[]{W64[id][0], S_X[0], S_Y[0]},
                                m, k, div);
                        getLongs(S_Y[2], y, m);
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_wput32(int id, int[] w, int m, int k) {
                    if (!kof_vk_available() || VK_PIPE32 == null) return -6;
                    if (id < 0 || id >= VK_WMAX || (long) m * k <= 0) return -2;
                    try {
                        var map = vkSlot(W32[id], W32CAP[id], (long) m * k * 4);
                        putInts(map, w, m * k);
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_wrun32(int id, long[] x, long[] y, int m, int k,
                        long div) {
                    if (!kof_vk_available() || VK_PIPE32 == null) return -6;
                    if (id < 0 || id >= VK_WMAX || W32[id][2] == null
                            || W32CAP[id][0] < (long) m * k * 4) return -2;
                    try {
                        vkGrow(S_X32, C_X32, (long) k * 8);
                        vkGrow(S_Y32, C_Y32, (long) m * 8);
                        putLongs(S_X32[2], x, k);
                        vkRunMV(VK_PIPE32, VK_DSET3, false,
                                new java.lang.foreign.MemorySegment[]{W32[id][0], S_X32[0], S_Y32[0]},
                                m, k, div);
                        getLongs(S_Y32[2], y, m);
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_wputsp(int id, int[] wh, int[] wl, int m, int k) {
                    if (!kof_vk_available() || VK_PIPESPL == null) return -6;
                    if (id < 0 || id >= VK_WMAX || (long) m * k <= 0) return -2;
                    try {
                        var mh = vkSlot(WH[id], WHCAP[id], (long) m * k * 4);
                        putInts(mh, wh, m * k);
                        var ml = vkSlot(WL[id], WLCAP[id], (long) m * k * 4);
                        putInts(ml, wl, m * k);
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_wrunsp(int id, long[] x, long[] y, int m, int k,
                        long div) {
                    if (!kof_vk_available() || VK_PIPESPL == null || VK_DSET5 == null) return -6;
                    if (id < 0 || id >= VK_WMAX || WH[id][2] == null
                            || WHCAP[id][0] < (long) m * k * 4
                            || WL[id][2] == null || WLCAP[id][0] < (long) m * k * 4) return -2;
                    try {
                        vkGrow(S_XH, C_XH, (long) k * 4);
                        vkGrow(S_XL, C_XL, (long) k * 4);
                        vkGrow(S_Y, C_Y, (long) m * 8);
                        // xh/xl no host (trunc, igual Kof): xh = x/1e6, xl = x%1e6
                        var xhmap = S_XH[2];
                        var xlmap = S_XL[2];
                        for (int i = 0; i < k; i++) {
                            long xi = x[i];
                            xhmap.setAtIndex(java.lang.foreign.ValueLayout.JAVA_INT, i,
                                    (int) (xi / 1000000));
                            xlmap.setAtIndex(java.lang.foreign.ValueLayout.JAVA_INT, i,
                                    (int) (xi % 1000000));
                        }
                        vkRunMV(VK_PIPESPL, VK_DSET5, true,
                                new java.lang.foreign.MemorySegment[]{WH[id][0], WL[id][0],
                                        S_XH[0], S_XL[0], S_Y[0]},
                                m, k, div);
                        getLongs(S_Y[2], y, m);
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                // ── matmul (gpu.dispatchMatmul / dispatchMatmul64) ────────────

                public static int kof_vk_dispatch(int[] a, int[] b, int[] c,
                        int m, int n, int k) {
                    if (!kof_vk_available() || VK_PIPEMM == null || VK_DSET12 == null) return -1;
                    try {
                        vkGrow(S_A, C_A, (long) m * k * 4);
                        vkGrow(S_B, C_B, (long) k * n * 4);
                        vkGrow(S_C, C_C, (long) m * n * 4);
                        putInts(S_A[2], a, m * k);
                        putInts(S_B[2], b, k * n);
                        vkRunMM(VK_PIPEMM, VK_DSET12, m, n, k);
                        for (int i = 0; i < m * n; i++) {
                            c[i] = S_C[2].getAtIndex(java.lang.foreign.ValueLayout.JAVA_INT, i);
                        }
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_vk_dispatch64(long[] a, long[] b, long[] c,
                        int m, int n, int k) {
                    if (!kof_vk_available() || VK_PIPEMM64 == null || VK_DSET12 == null) return -1;
                    try {
                        vkGrow(S_A64, C_A64, (long) m * k * 8);
                        vkGrow(S_B64, C_B64, (long) k * n * 8);
                        vkGrow(S_C64, C_C64, (long) m * n * 8);
                        putLongs(S_A64[2], a, m * k);
                        putLongs(S_B64[2], b, k * n);
                        vkBindBuf(VK_DSET12, 0, S_A64[0]);
                        vkBindBuf(VK_DSET12, 1, S_B64[0]);
                        vkBindBuf(VK_DSET12, 2, S_C64[0]);
                        var bbi = vkAlloc(32);
                        putI(bbi, 0, 42);
                        putI(bbi, 16, 1);
                        vk((int) vkBeginCommandBuffer.invoke(VK_CMD, bbi), "begin");
                        vkCmdBindPipeline.invoke(VK_CMD, 6, VK_PIPEMM64);
                        var dsets = vkAlloc(8);
                        putP(dsets, 0, VK_DSET12);
                        vkCmdBindDescriptorSets.invoke(VK_CMD, 6, VK_PL12, 0, 1, dsets, 0, vkNull());
                        var push = vkAlloc(12);
                        putI(push, 0, m);
                        putI(push, 4, n);
                        putI(push, 8, k);
                        vkCmdPushConstants.invoke(VK_CMD, VK_PL12, 0x20, 0, 12, push);
                        vkCmdDispatch.invoke(VK_CMD, m * n, 1, 1);
                        vkEndAndWait();
                        getLongs(S_C64[2], c, m * n);
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }
            }"""
    }
}
