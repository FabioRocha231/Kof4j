// vkchain64.c — M36 FASE C: matvec int64 com W residente.
// Contrato:
//   int  vkchain64_init(const char* spv_path)      → 0 ok (pipeline matvec)
//   const char* vkchain64_fail_reason(void)
//   void* vkchain64_mapped_w(void)                 → ptr host p/ escrever W
//   int   vkchain64_set_shape(int m, int k)        → dimensiona W/x/y (re-aloca
//           se precisar; W mapeado persistente HOST_VISIBLE|COHERENT)
//   int   vkchain64_matvec(long* x, long* y, int m, int k)
//           → 0 ok: lê W do buffer mapeado (sem copiar), x copiado, y copiado
//     != 0 (caller usa golden CPU)
// matvec64.comp: binding0=w readonly, binding1=x, binding2=y writeonly,
// push (m,k), local_size 64 (1 thread/row).
// Memória: heap 0 device-local 1.75GiB budget ~978MB; big W usa HOST_VISIBLE
// (host mem via PCIe: leitura ~6-12GB/s no Polaris12 — 25x vs CPU 12s/token).
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <vulkan/vulkan.h>

static VkInstance inst; static VkDevice dev; static VkQueue q; static int qfam;
static VkPhysicalDevice phys;
static VkPipeline pipe; static VkPipelineLayout pl;
static VkDescriptorPool dpool; static VkDescriptorSet dset;
static VkDescriptorSetLayout dsl;
static VkBuffer wbuf; static VkDeviceMemory wmem; static void* wmap;
static VkBuffer xbuf; static VkDeviceMemory xmem; static void* xmap;
static VkBuffer ybuf; static VkDeviceMemory ymem; static void* ymap;
static VkCommandPool cpool; static VkCommandBuffer cmd;
static VkFence fence;
static char errbuf[256] = "not initialized";
static int inited = 0;
static int wcapElems = 0;   // capacidade atual do W (elems int64)
static int xcapElems = 0;
static int ycapElems = 0;
static int curM = 0, curK = 0;

#define CK(x, msg) do { VkResult r=(x); if(r!=VK_SUCCESS){ snprintf(errbuf,sizeof errbuf,"%s (rc=%d)",msg,r); return 1; } } while(0)

const char* vkchain64_fail_reason(void){ return errbuf; }

// aloca buffer+mem host-visible coherent e mapeia
static int allocBuffer(VkBuffer* buf, VkDeviceMemory* mem, void** map, VkDeviceSize bytes){
    VkBufferCreateInfo bci={VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,0,0,bytes,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,VK_SHARING_MODE_EXCLUSIVE,0,0};
    CK(vkCreateBuffer(dev,&bci,0,buf), "buffer");
    VkMemoryRequirements req; vkGetBufferMemoryRequirements(dev,*buf,&req);
    VkPhysicalDeviceMemoryProperties mp;
    vkGetPhysicalDeviceMemoryProperties(phys,&mp);
    int mi=-1;
    for (uint32_t t=0;t<mp.memoryTypeCount;t++)
        if ((req.memoryTypeBits&(1u<<t))&&(mp.memoryTypes[t].propertyFlags&VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)&&(mp.memoryTypes[t].propertyFlags&VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)){mi=(int)t;break;}
    if (mi<0){ snprintf(errbuf,sizeof errbuf,"sem mem host-visible"); return 1; }
    VkMemoryAllocateInfo mai={VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,0,req.size,(uint32_t)mi};
    CK(vkAllocateMemory(dev,&mai,0,mem), "alloc mem");
    CK(vkBindBufferMemory(dev,*buf,*mem,0), "bind mem");
    CK(vkMapMemory(dev,*mem,0,bytes,0,map), "map");
    return 0;
}

static void freeBuffer(VkBuffer* buf, VkDeviceMemory* mem, void** map){
    if (*map) vkUnmapMemory(dev,*mem);
    if (*buf) vkDestroyBuffer(dev,*buf,0);
    if (*mem) vkFreeMemory(dev,*mem,0);
    *buf=0; *mem=0; *map=0;
}

int vkchain64_init(const char* spv_path){
    if (inited) return 0;
    VkApplicationInfo ai={VK_STRUCTURE_TYPE_APPLICATION_INFO,0,"kof",0,0,0,VK_API_VERSION_1_3};
    VkInstanceCreateInfo ici={VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,0,0,&ai,0,0,0,0};
    CK(vkCreateInstance(&ici,0,&inst), "instance");
    uint32_t n=0;
    CK(vkEnumeratePhysicalDevices(inst,&n,0), "enum0");
    if (n==0){ snprintf(errbuf,sizeof errbuf,"nenhum physical device"); return 1; }
    VkPhysicalDevice physv[4];
    CK(vkEnumeratePhysicalDevices(inst,&n,physv), "enum");
    phys = physv[0];
    uint32_t qn=0; vkGetPhysicalDeviceQueueFamilyProperties(phys,&qn,0);
    VkQueueFamilyProperties qf[8]; vkGetPhysicalDeviceQueueFamilyProperties(phys,&qn,qf);
    qfam=0; while(qfam<(int)qn && !(qf[qfam].queueFlags&VK_QUEUE_COMPUTE_BIT)) qfam++;
    if (qfam>=(int)qn){ snprintf(errbuf,sizeof errbuf,"sem compute queue"); return 1; }
    float prio=1.0f;
    VkDeviceQueueCreateInfo qci={VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,0,0,(uint32_t)qfam,1,&prio};
    VkDeviceCreateInfo dci={VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,0,0,1,&qci,0,0,0,0};
    CK(vkCreateDevice(phys,&dci,0,&dev), "device");
    vkGetDeviceQueue(dev,(uint32_t)qfam,0,&q);

    // SPIR-V
    FILE* f=fopen(spv_path,"rb");
    if(!f){ snprintf(errbuf,sizeof errbuf,"spv nao abriu: %s",spv_path); return 1; }
    fseek(f,0,SEEK_END); long sz=ftell(f); fseek(f,0,SEEK_SET);
    uint32_t* code=malloc((size_t)sz);
    if (fread(code,1,(size_t)sz,f)!=(size_t)sz){ snprintf(errbuf,sizeof errbuf,"spv leitura falhou"); return 1; }
    fclose(f);
    VkShaderModuleCreateInfo smci={VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO,0,0,(size_t)sz,code};
    VkShaderModule sm;
    CK(vkCreateShaderModule(dev,&smci,0,&sm), "shader module");
    free(code);

    // desc layout: 3 SSBOs
    VkDescriptorSetLayoutBinding binds[3];
    for (int b=0;b<3;b++) binds[b]=(VkDescriptorSetLayoutBinding){(uint32_t)b,VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,1,VK_SHADER_STAGE_COMPUTE_BIT,0};
    VkDescriptorSetLayoutCreateInfo dli={VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO,0,0,3,binds};
    CK(vkCreateDescriptorSetLayout(dev,&dli,0,&dsl), "desc layout");
    VkPushConstantRange pcr={VK_SHADER_STAGE_COMPUTE_BIT,0,8};
    VkPipelineLayoutCreateInfo pli={VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO,0,0,1,&dsl,1,&pcr};
    CK(vkCreatePipelineLayout(dev,&pli,0,&pl), "pipe layout");
    VkPipelineShaderStageCreateInfo stage={VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,0,0,VK_SHADER_STAGE_COMPUTE_BIT,sm,"main",0};
    VkComputePipelineCreateInfo pci={VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO,0,0,stage,pl,0,0};
    CK(vkCreateComputePipelines(dev,0,1,&pci,0,&pipe), "pipeline");

    // desc pool + set (atualizado em set_shape)
    VkDescriptorPoolSize ps={VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,3};
    VkDescriptorPoolCreateInfo dpi={VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO,0,0,1,1,&ps};
    CK(vkCreateDescriptorPool(dev,&dpi,0,&dpool), "desc pool");
    VkDescriptorSetAllocateInfo dsai={VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO,0,dpool,1,&dsl};
    CK(vkAllocateDescriptorSets(dev,&dsai,&dset), "desc set");

    // cmd pool/buffer + fence
    VkCommandPoolCreateInfo cpi={VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,0,0,(uint32_t)qfam};
    CK(vkCreateCommandPool(dev,&cpi,0,&cpool), "cmd pool");
    VkCommandBufferAllocateInfo cbai={VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,0,cpool,VK_COMMAND_BUFFER_LEVEL_PRIMARY,1};
    CK(vkAllocateCommandBuffers(dev,&cbai,&cmd), "cmd buf");
    VkFenceCreateInfo fci={VK_STRUCTURE_TYPE_FENCE_CREATE_INFO,0,0};
    CK(vkCreateFence(dev,&fci,0,&fence), "fence");
    inited=1;
    snprintf(errbuf,sizeof errbuf,"ok");
    return 0;
}

// dimensiona W[m×k], x[k], y[m] (re-aloca buffers que não comportam)
int vkchain64_set_shape(int m, int k){
    if (!inited) return -1;
    curM=m; curK=k;
    if ((long)m*k > wcapElems){
        freeBuffer(&wbuf,&wmem,&wmap);
        if (allocBuffer(&wbuf,&wmem,&wmap,(VkDeviceSize)m*k*8)) return -2;
        wcapElems=m*k;
    }
    if ((long)k > xcapElems){
        freeBuffer(&xbuf,&xmem,&xmap);
        if (allocBuffer(&xbuf,&xmem,&xmap,(VkDeviceSize)k*8)) return -2;
        xcapElems=k;
    }
    if ((long)m > ycapElems){
        freeBuffer(&ybuf,&ymem,&ymap);
        if (allocBuffer(&ybuf,&ymem,&ymap,(VkDeviceSize)m*8)) return -2;
        ycapElems=m;
    }
    // desc set com os buffers atuais
    VkDescriptorBufferInfo dbi[3];
    VkWriteDescriptorSet wds[3];
    dbi[0]=(VkDescriptorBufferInfo){wbuf,0,(VkDeviceSize)wcapElems*8};
    dbi[1]=(VkDescriptorBufferInfo){xbuf,0,(VkDeviceSize)xcapElems*8};
    dbi[2]=(VkDescriptorBufferInfo){ybuf,0,(VkDeviceSize)ycapElems*8};
    for (int i=0;i<3;i++)
        wds[i]=(VkWriteDescriptorSet){VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,0,dset,(uint32_t)i,0,1,VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,0,&dbi[i],0};
    vkUpdateDescriptorSets(dev,3,wds,0,0);
    return 0;
}

void* vkchain64_mapped_w(void){ return wmap; }

// copia long[] w (m×k) pro buffer W mapeado — DMA host→host-visible
int vkchain64_load_w(long* w, int m, int k){
    if (!inited) return -1;
    if (!wmap) return -2;
    if ((long)m*k > wcapElems) return -3;  // set_shape primeiro
    memcpy(wmap, w, (size_t)m*k*8);
    return 0;
}

int vkchain64_matvec(long* x, long* y, int m, int k){
    if (!inited) return -1;
    if (curM != m || curK != k) return -2;  // caller fez set_shape
    if (!wmap) return -3;
    // x → buffer mapeado (W JÁ está lá — o host escreveu direto via mapped_w)
    memcpy(xmap, x, (size_t)k*8);
    memset(ymap, 0, (size_t)m*8);

    VkCommandBufferBeginInfo bbi={VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,0,VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,0};
    VkResult r = vkBeginCommandBuffer(cmd,&bbi);
    if (r) { snprintf(errbuf,sizeof errbuf,"begin rc=%d",r); return -4; }
    vkCmdBindPipeline(cmd,VK_PIPELINE_BIND_POINT_COMPUTE,pipe);
    vkCmdBindDescriptorSets(cmd,VK_PIPELINE_BIND_POINT_COMPUTE,pl,0,1,&dset,0,0);
    int pcs[2]={m,k};
    vkCmdPushConstants(cmd,pl,VK_SHADER_STAGE_COMPUTE_BIT,0,8,pcs);
    vkCmdDispatch(cmd,(uint32_t)m,1,1);  // 1 workgroup = 1 row (64 threads)
    r = vkEndCommandBuffer(cmd);
    if (r) { snprintf(errbuf,sizeof errbuf,"end rc=%d",r); return -4; }
    VkSubmitInfo si={VK_STRUCTURE_TYPE_SUBMIT_INFO,0,0,0,0,1,&cmd,0,0};
    r = vkQueueSubmit(q,1,&si,fence);
    if (r) { snprintf(errbuf,sizeof errbuf,"submit rc=%d",r); return -4; }
    r = vkWaitForFences(dev,1,&fence,VK_TRUE,60000000000ull);
    if (r) { snprintf(errbuf,sizeof errbuf,"wait rc=%d",r); return -4; }
    vkResetFences(dev,1,&fence);
    memcpy(y, ymap, (size_t)m*8);
    return 0;
}
