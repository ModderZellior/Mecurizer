package net.caffeinemc.mods.sodium.client;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;

import static org.lwjgl.vulkan.VK10.*;

public final class MercurizerVulkanProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mercurizer");

    private static volatile MercurizerVulkanDeviceInfo cached;
    private static volatile boolean probed = false;

    private MercurizerVulkanProbe() {}

    public static MercurizerVulkanDeviceInfo probe() {
        if (probed) return cached;
        probed = true;
        try {
            cached = doProbe();
            if (cached != null) {
                LOGGER.info("[Mercurizer] Vulkan device: {} ({}, {} MB VRAM)",
                        cached.deviceName,
                        cached.isIntegrated ? "integrated" : "discrete",
                        cached.vramMb);
            } else {
                LOGGER.warn("[Mercurizer] Vulkan probe returned no usable device");
            }
        } catch (Exception e) {
            LOGGER.warn("[Mercurizer] Vulkan probe failed: {}", e.getMessage());
        }
        return cached;
    }

    private static MercurizerVulkanDeviceInfo doProbe() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .apiVersion(VK_API_VERSION_1_0);

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pApplicationInfo(appInfo);

            PointerBuffer pInstance = stack.mallocPointer(1);
            if (vkCreateInstance(createInfo, null, pInstance) != VK_SUCCESS) return null;
            VkInstance instance = new VkInstance(pInstance.get(0), createInfo);

            try {
                return pickBestDevice(instance);
            } finally {
                vkDestroyInstance(instance, null);
            }
        }
    }

    private static MercurizerVulkanDeviceInfo pickBestDevice(VkInstance instance) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            if (vkEnumeratePhysicalDevices(instance, count, null) != VK_SUCCESS) return null;
            if (count.get(0) == 0) return null;

            PointerBuffer devices = stack.mallocPointer(count.get(0));
            vkEnumeratePhysicalDevices(instance, count, devices);

            MercurizerVulkanDeviceInfo best = null;
            for (int i = 0; i < count.get(0); i++) {
                VkPhysicalDevice dev = new VkPhysicalDevice(devices.get(i), instance);
                MercurizerVulkanDeviceInfo info = readDeviceInfo(dev);
                if (info == null) continue;
                // prefer discrete over integrated
                if (best == null || (!info.isIntegrated && best.isIntegrated)) best = info;
            }
            return best;
        }
    }

    private static MercurizerVulkanDeviceInfo readDeviceInfo(VkPhysicalDevice dev) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(dev, props);

            int type = props.deviceType();
            if (type == VK_PHYSICAL_DEVICE_TYPE_CPU) return null;

            boolean integrated = (type == VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU);
            String name = props.deviceNameString();

            VkPhysicalDeviceMemoryProperties memProps = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(dev, memProps);
            long maxHeap = 0;
            for (int i = 0; i < memProps.memoryHeapCount(); i++) {
                VkMemoryHeap heap = memProps.memoryHeaps(i);
                if ((heap.flags() & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) != 0)
                    maxHeap = Math.max(maxHeap, heap.size());
            }

            return new MercurizerVulkanDeviceInfo(name, integrated, (int)(maxHeap / (1024 * 1024)));
        }
    }
}
