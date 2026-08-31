package net.caffeinemc.mods.sodium.client;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.*;

import static org.lwjgl.vulkan.VK10.*;

public final class MercurizerVulkanBenchmark {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mercurizer");

    static final int LARGE_SIZE = 4 * 1024 * 1024;
    static final int SMALL_SIZE = 128 * 1024;

    private static final int WARMUP_LARGE = 12;
    private static final int WARMUP_SMALL = 12;
    private static final int LATENCY_RUNS = 20;
    private static final int MIN_SAMPLES  = 10;
    private static final int MAX_SAMPLES  = 60;
    private static final double TARGET_COV = 0.08;

    private MercurizerVulkanBenchmark() {}

    public static double[] run() {
        try {
            return doRun();
        } catch (Exception e) {
            LOGGER.warn("[Mercurizer] Vulkan GPU benchmark failed: {}", e.getMessage());
            return null;
        }
    }

    private static double[] doRun() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .apiVersion(VK_API_VERSION_1_0);

            VkInstanceCreateInfo instanceCreateInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pApplicationInfo(appInfo);

            PointerBuffer pp = stack.mallocPointer(1);
            if (vkCreateInstance(instanceCreateInfo, null, pp) != VK_SUCCESS) return null;
            VkInstance instance = new VkInstance(pp.get(0), instanceCreateInfo);

            try {
                return setupAndRun(instance);
            } finally {
                vkDestroyInstance(instance, null);
            }
        }
    }

    private static double[] setupAndRun(VkInstance instance) {
        VkPhysicalDevice physDevice = null;
        int transferQueueFamily = -1;
        int queueTimestampBits = 0;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            vkEnumeratePhysicalDevices(instance, count, null);
            if (count.get(0) == 0) return null;

            PointerBuffer devices = stack.mallocPointer(count.get(0));
            vkEnumeratePhysicalDevices(instance, count, devices);

            for (int i = 0; i < count.get(0); i++) {
                VkPhysicalDevice dev = new VkPhysicalDevice(devices.get(i), instance);

                try (MemoryStack inner = MemoryStack.stackPush()) {
                    VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(inner);
                    vkGetPhysicalDeviceProperties(dev, props);
                    if (props.deviceType() == VK_PHYSICAL_DEVICE_TYPE_CPU) continue;

                    int[] queueInfo = findTransferQueue(dev, inner);
                    if (queueInfo == null) continue;

                    boolean discrete = props.deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU;
                    if (physDevice == null || discrete) {
                        physDevice = dev;
                        transferQueueFamily = queueInfo[0];
                        queueTimestampBits = queueInfo[1];
                        if (discrete) break;
                    }
                }
            }
        }

        if (physDevice == null) return null;

        float timestampPeriod = 1.0f;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(physDevice, props);
            timestampPeriod = props.limits().timestampPeriod();
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDeviceQueueCreateInfo.Buffer queueCreateBuf = VkDeviceQueueCreateInfo.calloc(1, stack);
            queueCreateBuf.get(0)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                    .queueFamilyIndex(transferQueueFamily)
                    .pQueuePriorities(stack.floats(1.0f));

            VkDeviceCreateInfo deviceCreate = VkDeviceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                    .pQueueCreateInfos(queueCreateBuf);

            PointerBuffer pp = stack.mallocPointer(1);
            if (vkCreateDevice(physDevice, deviceCreate, null, pp) != VK_SUCCESS) return null;
            VkDevice device = new VkDevice(pp.get(0), physDevice, deviceCreate);

            try {
                return runBenchmark(device, physDevice, transferQueueFamily, queueTimestampBits, timestampPeriod);
            } finally {
                vkDeviceWaitIdle(device);
                vkDestroyDevice(device, null);
            }
        }
    }

    // Returns { queueFamilyIndex, timestampValidBits } or null if no suitable queue
    private static int[] findTransferQueue(VkPhysicalDevice dev, MemoryStack stack) {
        IntBuffer qCount = stack.mallocInt(1);
        vkGetPhysicalDeviceQueueFamilyProperties(dev, qCount, null);
        VkQueueFamilyProperties.Buffer qProps = VkQueueFamilyProperties.calloc(qCount.get(0), stack);
        vkGetPhysicalDeviceQueueFamilyProperties(dev, qCount, qProps);

        for (int i = 0; i < qCount.get(0); i++) {
            if ((qProps.get(i).queueFlags() & VK_QUEUE_TRANSFER_BIT) != 0) {
                return new int[]{ i, qProps.get(i).timestampValidBits() };
            }
        }
        return null;
    }

    private static double[] runBenchmark(VkDevice device, VkPhysicalDevice physDevice,
                                          int queueFamily, int timestampBits, float timestampPeriod) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pp = stack.mallocPointer(1);
            vkGetDeviceQueue(device, queueFamily, 0, pp);
            VkQueue queue = new VkQueue(pp.get(0), device);

            LongBuffer pPool = stack.mallocLong(1);
            if (vkCreateCommandPool(device, VkCommandPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                    .queueFamilyIndex(queueFamily)
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT), null, pPool) != VK_SUCCESS) return null;
            long cmdPool = pPool.get(0);

            PointerBuffer pCb = stack.mallocPointer(1);
            if (vkAllocateCommandBuffers(device, VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(cmdPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1), pCb) != VK_SUCCESS) {
                vkDestroyCommandPool(device, cmdPool, null);
                return null;
            }
            VkCommandBuffer cb = new VkCommandBuffer(pCb.get(0), device);

            LongBuffer pFence = stack.mallocLong(1);
            vkCreateFence(device, VkFenceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO), null, pFence);
            long fence = pFence.get(0);

            // create timestamp query pool if the queue supports it
            long queryPool = VK_NULL_HANDLE;
            if (timestampBits > 0) {
                LongBuffer pQP = stack.mallocLong(1);
                if (vkCreateQueryPool(device, VkQueryPoolCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO)
                        .queryType(VK_QUERY_TYPE_TIMESTAMP)
                        .queryCount(2), null, pQP) == VK_SUCCESS) {
                    queryPool = pQP.get(0);
                }
            }

            if (queryPool == VK_NULL_HANDLE) {
                LOGGER.info("[Mercurizer] Vulkan GPU timestamps not supported — using wall-clock timing");
            }

            VkPhysicalDeviceMemoryProperties memProps = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physDevice, memProps);

            try {
                return measure(device, queue, cb, fence, queryPool, timestampPeriod, memProps);
            } finally {
                if (queryPool != VK_NULL_HANDLE) vkDestroyQueryPool(device, queryPool, null);
                vkDestroyFence(device, fence, null);
                vkDestroyCommandPool(device, cmdPool, null);
            }
        }
    }

    private static double[] measure(VkDevice device, VkQueue queue, VkCommandBuffer cb,
                                     long fence, long queryPool, float timestampPeriod,
                                     VkPhysicalDeviceMemoryProperties memProps) {
        long largeSrc = VK_NULL_HANDLE, largeSrcMem = VK_NULL_HANDLE;
        long largeDst = VK_NULL_HANDLE, largeDstMem = VK_NULL_HANDLE;
        long smallSrc = VK_NULL_HANDLE, smallSrcMem = VK_NULL_HANDLE;
        long smallDst = VK_NULL_HANDLE, smallDstMem = VK_NULL_HANDLE;

        try {
            long[] largeSrcPair = allocBuf(device, memProps, LARGE_SIZE,
                    VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            if (largeSrcPair == null) return null;
            largeSrc = largeSrcPair[0]; largeSrcMem = largeSrcPair[1];

            long[] largeDstPair = allocBuf(device, memProps, LARGE_SIZE,
                    VK_BUFFER_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            if (largeDstPair == null) return null;
            largeDst = largeDstPair[0]; largeDstMem = largeDstPair[1];

            long[] smallSrcPair = allocBuf(device, memProps, SMALL_SIZE,
                    VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            if (smallSrcPair == null) return null;
            smallSrc = smallSrcPair[0]; smallSrcMem = smallSrcPair[1];

            long[] smallDstPair = allocBuf(device, memProps, SMALL_SIZE,
                    VK_BUFFER_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            if (smallDstPair == null) return null;
            smallDst = smallDstPair[0]; smallDstMem = smallDstPair[1];

            fillStaging(device, largeSrcMem, LARGE_SIZE);
            fillStaging(device, smallSrcMem, SMALL_SIZE);

            for (int i = 0; i < WARMUP_LARGE; i++) copy(device, queue, cb, fence, queryPool, timestampPeriod, largeSrc, largeDst, LARGE_SIZE);
            for (int i = 0; i < WARMUP_SMALL; i++) copy(device, queue, cb, fence, queryPool, timestampPeriod, smallSrc, smallDst, SMALL_SIZE);

            long[] largeSamples = adaptiveSample(device, queue, cb, fence, queryPool, timestampPeriod, largeSrc, largeDst, LARGE_SIZE);
            long[] smallSamples = adaptiveSample(device, queue, cb, fence, queryPool, timestampPeriod, smallSrc, smallDst, SMALL_SIZE);

            long[] latSamples = new long[LATENCY_RUNS];
            for (int i = 0; i < LATENCY_RUNS; i++)
                latSamples[i] = copy(device, queue, cb, fence, queryPool, timestampPeriod, smallSrc, smallDst, SMALL_SIZE);

            double largeBw  = bw(largeSamples, LARGE_SIZE);
            double smallBw  = bw(smallSamples, SMALL_SIZE);
            double rtNs     = MercurizerBenchmark.trimmedMean(MercurizerBenchmark.removeSpikesByMedian(latSamples));
            double worstCov = Math.max(
                    MercurizerBenchmark.coefficientOfVariation(largeSamples),
                    MercurizerBenchmark.coefficientOfVariation(smallSamples));

            return new double[]{ largeBw, smallBw, rtNs, worstCov };

        } finally {
            freeBuf(device, largeSrc, largeSrcMem);
            freeBuf(device, largeDst, largeDstMem);
            freeBuf(device, smallSrc, smallSrcMem);
            freeBuf(device, smallDst, smallDstMem);
        }
    }

    private static long[] adaptiveSample(VkDevice device, VkQueue queue, VkCommandBuffer cb,
                                          long fence, long queryPool, float timestampPeriod,
                                          long src, long dst, int size) {
        long[] buf = new long[MAX_SAMPLES];
        int count = 0;
        while (count < MAX_SAMPLES) {
            buf[count++] = copy(device, queue, cb, fence, queryPool, timestampPeriod, src, dst, size);
            if (count >= MIN_SAMPLES) {
                long[] slice = java.util.Arrays.copyOf(buf, count);
                if (MercurizerBenchmark.coefficientOfVariation(slice) <= TARGET_COV) break;
            }
        }
        return java.util.Arrays.copyOf(buf, count);
    }

    private static long copy(VkDevice device, VkQueue queue, VkCommandBuffer cb, long fence,
                              long queryPool, float timestampPeriod,
                              long src, long dst, int size) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkResetFences(device, fence);

            vkBeginCommandBuffer(cb, VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT));

            if (queryPool != VK_NULL_HANDLE) {
                vkCmdResetQueryPool(cb, queryPool, 0, 2);
                vkCmdWriteTimestamp(cb, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, queryPool, 0);
            }

            vkCmdCopyBuffer(cb, src, dst, VkBufferCopy.calloc(1, stack).size(size));

            if (queryPool != VK_NULL_HANDLE) {
                vkCmdWriteTimestamp(cb, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, queryPool, 1);
            }

            vkEndCommandBuffer(cb);

            long t0 = System.nanoTime();
            vkQueueSubmit(queue, VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(stack.pointers(cb)), fence);
            vkWaitForFences(device, fence, true, Long.MAX_VALUE);
            long wallNs = System.nanoTime() - t0;

            if (queryPool != VK_NULL_HANDLE) {
                LongBuffer timestamps = stack.mallocLong(2);
                int result = vkGetQueryPoolResults(device, queryPool, 0, 2,
                        timestamps, Long.BYTES,
                        VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WAIT_BIT);
                if (result == VK_SUCCESS) {
                    long ticks = timestamps.get(1) - timestamps.get(0);
                    return (long)(ticks * timestampPeriod);
                }
            }

            return wallNs;
        }
    }

    private static long[] allocBuf(VkDevice device, VkPhysicalDeviceMemoryProperties memProps,
                                    int size, int usage, int memFlags) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pBuf = stack.mallocLong(1);
            if (vkCreateBuffer(device, VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(size).usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE), null, pBuf) != VK_SUCCESS) return null;
            long buf = pBuf.get(0);

            VkMemoryRequirements reqs = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, buf, reqs);

            int memType = pickMemoryType(memProps, reqs.memoryTypeBits(), memFlags);
            if (memType < 0) {
                vkDestroyBuffer(device, buf, null);
                return null;
            }

            LongBuffer pMem = stack.mallocLong(1);
            if (vkAllocateMemory(device, VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(reqs.size())
                    .memoryTypeIndex(memType), null, pMem) != VK_SUCCESS) {
                vkDestroyBuffer(device, buf, null);
                return null;
            }
            long mem = pMem.get(0);
            vkBindBufferMemory(device, buf, mem, 0);
            return new long[]{ buf, mem };
        }
    }

    private static int pickMemoryType(VkPhysicalDeviceMemoryProperties props, int typeBits, int flags) {
        for (int i = 0; i < props.memoryTypeCount(); i++) {
            if ((typeBits & (1 << i)) != 0 && (props.memoryTypes(i).propertyFlags() & flags) == flags)
                return i;
        }
        if ((flags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0) {
            return pickMemoryType(props, typeBits,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        }
        return -1;
    }

    private static void freeBuf(VkDevice device, long buf, long mem) {
        if (buf != VK_NULL_HANDLE) vkDestroyBuffer(device, buf, null);
        if (mem != VK_NULL_HANDLE) vkFreeMemory(device, mem, null);
    }

    private static void fillStaging(VkDevice device, long mem, int size) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pp = stack.mallocPointer(1);
            if (vkMapMemory(device, mem, 0, size, 0, pp) != VK_SUCCESS) return;
            ByteBuffer mapped = MemoryUtil.memByteBuffer(pp.get(0), size);
            for (int i = 0; i < size; i++) mapped.put(i, (byte)(i & 0xFF));
            vkUnmapMemory(device, mem);
        }
    }

    private static double bw(long[] samples, int size) {
        double mean = MercurizerBenchmark.trimmedMean(MercurizerBenchmark.removeSpikesByMedian(samples));
        return mean > 0 ? (size * 1e9) / (mean * 1024.0 * 1024.0) : 1.0;
    }
}
