package dev.kof.compiler;

/**
 * M36.4: Vulkan compute 100% FFM (java.lang.foreign) — substitui a
 * libvkchain*.so (C). O text block {@link #VK_SOURCE} é injetado no
 * KofRuntime.java gerado (mesma classe) quando o programa usa gpu.* /
 * kof.vk / kof_mv64_* — o runtime gerado fica autocontido (sem jar).
 *
 * Cadeia (idêntica ao vkchain64.c): vkCreateInstance →
 * vkEnumeratePhysicalDevices → vkCreateDevice → vkGetDeviceQueue →
 * vkCreateShaderModule (SPIR-V de gpu/shaders/*.spv) → descriptor set
 * layout (3 SSBOs: matvec/matmul; 5: matvecsplit) → pipeline layout
 * (push constants 24B) → vkCreateComputePipelines (stage EMBUTIDO no
 * VkComputePipelineCreateInfo @24, 48B) → buffers host-visible
 * (HOST_VISIBLE|HOST_COHERENT via vkMapMemory) → descriptor pool/set →
 * command buffer + dispatch + fence.
 *
 * Structs Vulkan escritas byte a byte (offsets AMD64 x86-64 verificados
 * contra vulkan_core.h 1.3.275):
 *   VkApplicationInfo 40B  VkInstanceCreateInfo 64B
 *   VkDeviceQueueCreateInfo 40B  VkDeviceCreateInfo 72B (pEnabledFeatures@64)
 *   VkShaderModuleCreateInfo 40B  VkDescriptorSetLayoutBinding 24B
 *   VkDescriptorSetLayoutCreateInfo 32B  VkPushConstantRange 12B
 *   VkPipelineLayoutCreateInfo 48B  VkPipelineShaderStageCreateInfo 48B
 *   VkComputePipelineCreateInfo 96B (stage inline @24)  VkDescriptorPoolSize 8B
 *   VkDescriptorPoolCreateInfo 40B  VkDescriptorSetAllocateInfo 40B
 *   VkCommandPoolCreateInfo 24B  VkCommandBufferAllocateInfo 32B
 *   VkFenceCreateInfo 24B  VkBufferCreateInfo 56B  VkMemoryRequirements 24B
 *   VkPhysicalDeviceMemoryProperties 520B (types@4 32×8B, heaps@264 16×16B)
 *   VkMemoryAllocateInfo 32B  VkDescriptorBufferInfo 24B
 *   VkWriteDescriptorSet 64B (pBufferInfo@48)  VkCommandBufferBeginInfo 32B
 *   VkSubmitInfo 72B (pCommandBuffers@48)
 *
 * Pipelines: matvec64 (W i64 residente), matvecw32 (W i32 — metade do
 * PCIe), matvecsplit (wh/wl/xh/xl i32, y i64 bit-exato com o CPU).
 * SPVs por env: KOF_GPU_SPV64 / _W32 / _SPLIT / KOF_GPU_SPV (matmul32),
 * default gpu/shaders/*.spv. Pipeline ausente → o caminho degrada (rc != 0)
 * e o caller usa o golden CPU — nunca derruba o programa.
 *
 * Lições do debug (2026-08-30..09-04): stage inline no ComputePipelineInfo
 * (offset 24, 48B); RADV Polaris12 ok com i64 nativo (shaderInt64=true);
 * heap segment é rejeitado em downcall no JDK 22+ (usar Arena); o lançador
 * Kof já roda com --enable-native-access=ALL-UNNAMED.
 */
final class JvmVkRuntime {
    private JvmVkRuntime() {}

    static String source() {
        return VK_SOURCE;
    }

    private static final String VK_SOURCE = """
                // ── kof.vulkan — Vulkan compute 100% FFM (M36.4, sem libvkchain) ─
                // Toda a cadeia Vulkan vive aqui (structs byte a byte, downcalls
                // diretos da libvulkan.so.1). Qualquer falha degrada p/ false →
                // goldens CPU. O programa nunca cai.
                private static final int VK_WMAX = 192;
                private static volatile boolean VK_INITED = false;
                private static volatile boolean VK_OK = false;
                private static String VK_ERR = "not initialized";

                // handles vk*
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
                private static java.lang.invoke.MethodHandle vkDestroyInstance;
                private static java.lang.invoke.MethodHandle vkDestroyDevice;

                // objetos Vulkan (MemorySegment = handle; NULL = ausente)
                private static java.lang.foreign.MemorySegment VK_INST;
                private static java.lang.foreign.MemorySegment VK_PHYS;
                private static java.lang.foreign.MemorySegment VK_DEV;
                private static java.lang.foreign.MemorySegment VK_QUEUE;
                private static java.lang.foreign.MemorySegment VK_CMD;
                private static java.lang.foreign.MemorySegment VK_FENCE;
                private static java.lang.foreign.MemorySegment VK_PL;      // layout 3 binds
                private static java.lang.foreign.MemorySegment VK_PL5;     // layout 5 binds
                private static java.lang.foreign.MemorySegment VK_PIPE64;  // matvec64/matmul64
                private static java.lang.foreign.MemorySegment VK_PIPE32;  // matvecw32
                private static java.lang.foreign.MemorySegment VK_PIPESPL; // matvecsplit
                private static java.lang.foreign.MemorySegment VK_DSET;    // desc set 3 binds
                private static java.lang.foreign.MemorySegment VK_DSET5;   // desc set 5 binds
                private static java.lang.foreign.Arena VK_ARENA;

                // buffers x/y centrais (matvec64 e split compartilham y)
                private static java.lang.foreign.MemorySegment VK_XBUF, VK_XMEM, VK_XMAP;
                private static long VK_XCAP;
                private static java.lang.foreign.MemorySegment VK_YBUF, VK_YMEM, VK_YMAP;
                private static long VK_YCAP;
                // matvecw32: x/y i64 próprios (mesma forma)
                private static java.lang.foreign.MemorySegment VK_XBUF32, VK_XMEM32, VK_XMAP32;
                private static long VK_XCAP32;
                private static java.lang.foreign.MemorySegment VK_YBUF32, VK_YMEM32, VK_YMAP32;
                private static long VK_YCAP32;
                // split: xh/xl i32 por dispatch
                private static java.lang.foreign.MemorySegment VK_XHBUF, VK_XHMEM, VK_XHMAP;
                private static long VK_XHCAP;
                private static java.lang.foreign.MemorySegment VK_XLBUF, VK_XLMEM, VK_XLMAP;
                private static long VK_XLCAP;
                // matmul: buffers a/b/c (dispatch por call; residem entre calls
                // com o maior shape visto)
                private static java.lang.foreign.MemorySegment VK_ABUF, VK_AMEM, VK_AMAP;
                private static long VK_ACAP;
                private static java.lang.foreign.MemorySegment VK_BBUF, VK_BMEM, VK_BMAP;
                private static long VK_BCAP;
                private static java.lang.foreign.MemorySegment VK_CBUF, VK_CMEM, VK_CMAP;
                private static long VK_CCAP;
                private static java.lang.foreign.MemorySegment VK_ABUF64, VK_AMEM64, VK_AMAP64;
                private static long VK_ACAP64;
                private static java.lang.foreign.MemorySegment VK_BBUF64, VK_BMEM64, VK_BMAP64;
                private static long VK_BCAP64;
                private static java.lang.foreign.MemorySegment VK_CBUF64, VK_CMEM64, VK_CMAP64;
                private static long VK_CCAP64;

                // W residente por id (i64), W i32 por id, wh/wl split por id
                private static final java.lang.foreign.MemorySegment[] W64B = new java.lang.foreign.MemorySegment[VK_WMAX];
                private static final java.lang.foreign.MemorySegment[] W64M = new java.lang.foreign.MemorySegment[VK_WMAX];
                private static final java.lang.foreign.MemorySegment[] W64P = new java.lang.foreign.MemorySegment[VK_WMAX];
                private static final long[] W64C = new long[VK_WMAX];
                private static final java.lang.foreign.MemorySegment[] W32B = new java.lang.foreign.MemorySegment[VK_WMAX];
                private static final java.lang.foreign.MemorySegment[] W32M = new java.lang.foreign.MemorySegment[VK_WMAX];
                private static final java.lang.foreign.MemorySegment[] W32P = new java.lang.foreign.MemorySegment[VK_WMAX];
                private static final long[] W32C = new long[VK_WMAX];
                private static final java.lang.foreign.MemorySegment[] WHB = new java.lang.foreign.MemorySegment[VK_WMAX];
                private static final java.lang.foreign.MemorySegment[] WHM = new java.lang.foreign.MemorySegment[VK_WMAX];
                private static final java.lang.foreign.MemorySegment[] WHP = new java.lang.foreign.MemorySegment[VK_WMAX];
                private static final long[] WHC = new long[VK_WMAX];
                private static final java.lang.foreign.MemorySegment[] WLB = new java.lang.foreign.MemorySegment[VK_WMAX];
                private static final java.lang.foreign.MemorySegment[] WLM = new java.lang.foreign.MemorySegment[VK_WMAX];
                private static final java.lang.foreign.MemorySegment[] WLP = new java.lang.foreign.MemorySegment[VK_WMAX];
                private static final long[] WLC = new long[VK_WMAX];

                private static int VK_CURM = 0, VK_CURK = 0;

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

                private static java.lang.invoke.MethodHandle vkFn(
                        java.lang.foreign.SymbolLookup lib, String name,
                        java.lang.foreign.FunctionDescriptor desc) throws Throwable {
                    var linker = java.lang.foreign.Linker.nativeLinker();
                    return linker.downcallHandle(lib.find(name).orElseThrow(), desc);
                }

                private static boolean vkInitAll() {
                    if (VK_INST != null && VK_DEV != null) return true;
                    try {
                        VK_ARENA = java.lang.foreign.Arena.ofAuto();
                        var lib = java.lang.foreign.SymbolLookup.libraryLookup("libvulkan.so.1", VK_ARENA);
                        var I = java.lang.foreign.ValueLayout.JAVA_INT;
                        var L = java.lang.foreign.ValueLayout.JAVA_LONG;
                        var P = java.lang.foreign.ValueLayout.ADDRESS;
                        var F = java.lang.foreign.FunctionDescriptor;
                        vkCreateInstance = vkFn(lib, "vkCreateInstance", F.of(I, P, P, P));
                        vkEnumeratePhysicalDevices = vkFn(lib, "vkEnumeratePhysicalDevices", F.of(I, P, P, P));
                        vkGetPhysicalDeviceQueueFamilyProperties = vkFn(lib,
                                "vkGetPhysicalDeviceQueueFamilyProperties", F.ofVoid(P, P, P));
                        vkCreateDevice = vkFn(lib, "vkCreateDevice", F.of(I, P, P, P, P));
                        vkGetDeviceQueue = vkFn(lib, "vkGetDeviceQueue", F.ofVoid(P, I, I, P));
                        vkCreateShaderModule = vkFn(lib, "vkCreateShaderModule", F.of(I, P, P, P));
                        vkCreateDescriptorSetLayout = vkFn(lib, "vkCreateDescriptorSetLayout", F.of(I, P, P, P));
                        vkCreatePipelineLayout = vkFn(lib, "vkCreatePipelineLayout", F.of(I, P, P, P));
                        vkCreateComputePipelines = vkFn(lib, "vkCreateComputePipelines", F.of(I, P, P, I, P, P, P));
                        vkCreateDescriptorPool = vkFn(lib, "vkCreateDescriptorPool", F.of(I, P, P, P));
                        vkAllocateDescriptorSets = vkFn(lib, "vkAllocateDescriptorSets", F.of(I, P, P));
                        vkCreateCommandPool = vkFn(lib, "vkCreateCommandPool", F.of(I, P, P, P));
                        vkAllocateCommandBuffers = vkFn(lib, "vkAllocateCommandBuffers", F.of(I, P, P));
                        vkCreateFence = vkFn(lib, "vkCreateFence", F.of(I, P, P, P));
                        vkCreateBuffer = vkFn(lib, "vkCreateBuffer", F.of(I, P, P, P));
                        vkGetBufferMemoryRequirements = vkFn(lib, "vkGetBufferMemoryRequirements", F.ofVoid(P, P));
                        vkGetPhysicalDeviceMemoryProperties = vkFn(lib,
                                "vkGetPhysicalDeviceMemoryProperties", F.ofVoid(P, P));
                        vkAllocateMemory = vkFn(lib, "vkAllocateMemory", F.of(I, P, P, P));
                        vkBindBufferMemory = vkFn(lib, "vkBindBufferMemory", F.of(I, P, P, L, P));
                        vkMapMemory = vkFn(lib, "vkMapMemory", F.of(I, P, L, L, I, P));
                        vkUnmapMemory = vkFn(lib, "vkUnmapMemory", F.ofVoid(P, P));
                        vkDestroyBuffer = vkFn(lib, "vkDestroyBuffer", F.ofVoid(P, P, P));
                        vkFreeMemory = vkFn(lib, "vkFreeMemory", F.ofVoid(P, P, P));
                        vkUpdateDescriptorSets = vkFn(lib, "vkUpdateDescriptorSets", F.ofVoid(P, I, P, I, P));
                        vkBeginCommandBuffer = vkFn(lib, "vkBeginCommandBuffer", F.of(I, P, P));
                        vkCmdBindPipeline = vkFn(lib, "vkCmdBindPipeline", F.ofVoid(P, I, P));
                        vkCmdBindDescriptorSets = vkFn(lib, "vkCmdBindDescriptorSets",
                                F.ofVoid(P, I, P, I, I, P, I, P));
                        vkCmdPushConstants = vkFn(lib, "vkCmdPushConstants", F.ofVoid(P, P, I, I, I, P));
                        vkCmdDispatch = vkFn(lib, "vkCmdDispatch", F.ofVoid(P, I, I, I));
                        vkEndCommandBuffer = vkFn(lib, "vkEndCommandBuffer", F.of(I, P));
                        vkQueueSubmit = vkFn(lib, "vkQueueSubmit", F.of(I, P, I, P, P));
                        vkWaitForFences = vkFn(lib, "vkWaitForFences", F.of(I, P, I, I, L));
                        vkResetFences = vkFn(lib, "vkResetFences", F.of(I, P, I, P));
                        vkDestroyInstance = vkFn(lib, "vkDestroyInstance", F.ofVoid(P, P));
                        vkDestroyDevice = vkFn(lib, "vkDestroyDevice", F.ofVoid(P, P));
                        vkInitChain();
                        return true;
                    } catch (Throwable t) {
                        VK_ERR = t.getClass().getSimpleName() + ": " + t.getMessage();
                        return false;
                    }
                }

                // helpers de struct: ciente de que todo campo pointer é 8B e
                // todo int 4B (AMD64); sType sempre no offset 0.
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

                private static java.lang.foreign.MemorySegment vkCstr(String s) {
                    byte[] b = (s + "\\0").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    var seg = VK_ARENA.allocate(b.length);
                    java.lang.foreign.MemorySegment.copy(java.lang.foreign.MemorySegment.ofArray(b),
                            0, seg, 0, b.length);
                    return seg;
                }

                private static int vk(int rc, String what) throws Throwable {
                    if (rc != 0) {
                        VK_ERR = what + " (rc=" + (-rc) + ")";
                        throw new RuntimeException(VK_ERR);
                    }
                    return rc;
                }

                private static java.lang.foreign.MemorySegment vkOut() {
                    return vkAlloc(8);
                }

                private static java.lang.foreign.MemorySegment vkRes(java.lang.foreign.MemorySegment out) {
                    return out.get(java.lang.foreign.ValueLayout.ADDRESS, 0);
                }

                private static void vkInitChain() throws Throwable {
                    var a = VK_ARENA;
                    // instance
                    var ai = vkAlloc(40);
                    putI(ai, 0, 0);            // APPLICATION_INFO
                    putL(ai, 16, 0);           // pApplicationName
                    putI(ai, 32, 0x00403000);  // VK_API_VERSION_1_3
                    var ici = vkAlloc(64);
                    putI(ici, 0, 1);           // INSTANCE_CREATE_INFO
                    putL(ici, 8, 0);
                    putP(ici, 24, ai);
                    var instOut = vkOut();
                    vk((int) vkCreateInstance.invoke(ici, java.lang.foreign.MemorySegment.NULL, instOut), "instance");
                    VK_INST = vkRes(instOut);

                    // physical device 0
                    var nOut = vkAlloc(8);
                    putI(nOut, 0, 0);
                    vk((int) vkEnumeratePhysicalDevices.invoke(VK_INST, nOut,
                            java.lang.foreign.MemorySegment.NULL), "enum0");
                    int n = getI(nOut, 0);
                    if (n == 0) {
                        VK_ERR = "nenhum physical device";
                        throw new RuntimeException(VK_ERR);
                    }
                    var physv = a.allocate(8 * Math.min(n, 4));
                    vk((int) vkEnumeratePhysicalDevices.invoke(VK_INST, nOut, physv), "enum");
                    VK_PHYS = getP(physv, 0);

                    // queue family compute
                    var qnOut = vkAlloc(8);
                    putI(qnOut, 0, 0);
                    vkGetPhysicalDeviceQueueFamilyProperties.invoke(VK_PHYS, qnOut,
                            java.lang.foreign.MemorySegment.NULL);
                    int qn = Math.min(getI(qnOut, 0), 8);
                    var qf = a.allocate(8L * qn);
                    vkGetPhysicalDeviceQueueFamilyProperties.invoke(VK_PHYS, qnOut, qf);
                    int qfam = -1;
                    for (int i = 0; i < qn; i++) {
                        if ((getI(qf, (long) i * 8) & 0x2) != 0) {  // QUEUE_COMPUTE_BIT
                            qfam = i;
                            break;
                        }
                    }
                    if (qfam < 0) {
                        VK_ERR = "sem compute queue";
                        throw new RuntimeException(VK_ERR);
                    }

                    // device
                    var prio = a.allocate(4);
                    prio.set(java.lang.foreign.ValueLayout.JAVA_FLOAT, 0, 1.0f);
                    var qci = vkAlloc(40);
                    putI(qci, 0, 2);           // DEVICE_QUEUE_CREATE_INFO
                    putI(qci, 20, qfam);
                    putI(qci, 24, 1);
                    putP(qci, 32, prio);
                    var dci = vkAlloc(72);
                    putI(dci, 0, 3);           // DEVICE_CREATE_INFO
                    putI(dci, 20, 1);
                    putP(dci, 24, qci);
                    putL(dci, 64, 0);          // pEnabledFeatures NULL
                    var devOut = vkOut();
                    vk((int) vkCreateDevice.invoke(VK_PHYS, dci, java.lang.foreign.MemorySegment.NULL, devOut), "device");
                    VK_DEV = vkRes(devOut);
                    var qOut = vkOut();
                    vkGetDeviceQueue.invoke(VK_DEV, qfam, 0, qOut);
                    VK_QUEUE = vkRes(qOut);

                    // pipelines: matvec64 (3 binds, push 24B), w32, split (5 binds)
                    VK_PL = vkMakeLayout(3);
                    VK_PIPE64 = vkMakePipe(envSpv("KOF_GPU_SPV64", "gpu/shaders/matvec64.spv"), VK_PL);
                    VK_PIPE32 = vkMakePipeOpt(envSpv("KOF_GPU_SPV64_W32", "gpu/shaders/matvecw32.spv"), VK_PL);
                    VK_PL5 = vkMakeLayout(5);
                    VK_PIPESPL = vkMakePipeOpt(envSpv("KOF_GPU_SPV64_SPLIT", "gpu/shaders/matvecsplit.spv"), VK_PL5);
                    VK_DSET = vkMakeSet(3, VK_PL);
                    VK_DSET5 = (VK_PIPESPL != null) ? vkMakeSet(5, VK_PL5) : null;

                    // matmul32 (o matmul.spv original: 3 SSBOs i32 + push 12B)
                    VK_PIPE_MM32 = vkMakePipeOpt(envSpv("KOF_GPU_SPV", "gpu/shaders/matmul.spv"), VK_PL_MM);
                    if (VK_PIPE_MM32 == null) {
                        // tenta o layout de 12B de push próprio
                    }
                    // matmul64
                    VK_PIPE_MM64 = vkMakePipeOpt(envSpv("KOF_GPU_SPV64_MM", "gpu/shaders/matmul64.spv"), VK_PL_MM);

                    // cmd pool/buffer + fence
                    var cpi = vkAlloc(24);
                    putI(cpi, 0, 39);          // COMMAND_POOL_CREATE_INFO
                    putI(cpi, 20, qfam);
                    var cpOut = vkOut();
                    vk((int) vkCreateCommandPool.invoke(VK_DEV, cpi, java.lang.foreign.MemorySegment.NULL, cpOut), "cmd pool");
                    var cpool = vkRes(cpOut);
                    var cbai = vkAlloc(32);
                    putI(cbai, 0, 40);         // COMMAND_BUFFER_ALLOCATE_INFO
                    putP(cbai, 16, cpool);
                    putI(cbai, 24, 0);         // LEVEL_PRIMARY
                    putI(cbai, 28, 1);
                    var cbOut = vkOut();
                    vk((int) vkAllocateCommandBuffers.invoke(VK_DEV, cbai, cbOut), "cmd buf");
                    VK_CMD = vkRes(cbOut);
                    var fci = vkAlloc(24);
                    putI(fci, 0, 8);           // FENCE_CREATE_INFO
                    var fOut = vkOut();
                    vk((int) vkCreateFence.invoke(VK_DEV, fci, java.lang.foreign.MemorySegment.NULL, fOut), "fence");
                    VK_FENCE = vkRes(fOut);
                    VK_ERR = "ok";
                }
            }"""
    }
}
