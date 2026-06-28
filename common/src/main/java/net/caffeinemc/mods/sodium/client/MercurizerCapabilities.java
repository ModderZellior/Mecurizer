package net.caffeinemc.mods.sodium.client;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL43;

import java.util.HashSet;
import java.util.Set;

public final class MercurizerCapabilities {
    private static final int GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX = 0x9048;
    private static final int GL_TEXTURE_FREE_MEMORY_ATI = 0x87FC;
    private static final int GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF;
    private static final int GL_MAJOR_VERSION = 0x821B;
    private static final int GL_MINOR_VERSION = 0x821C;
    private static final int GL_NUM_EXTENSIONS = 0x821D;

    private static volatile MercurizerCapabilities cached;

    public final boolean isVulkan;
    public final int totalVramMb;
    public final int freeVramMb;
    public final int maxUniformBlockSize;
    public final int maxVertexAttribs;
    public final int maxVertexUniformBlocks;
    public final boolean hasDirectStateAccess;
    public final boolean hasMultiDrawIndirect;
    public final boolean hasBufferStorage;

    public final int maxTextureSize;
    public final int maxTextureImageUnits;
    public final int maxArrayTextureLayers;
    public final boolean hasTextureStorage;
    public final boolean hasAnisotropicFiltering;
    public final float maxAnisotropy;
    public final boolean hasS3tcCompression;
    public final boolean hasRgtcCompression;

    public final int maxComputeWorkGroupSizeX;
    public final boolean hasShaderStorageBufferObject;
    public final int maxFragmentUniformComponents;

    public final boolean hasSyncObjects;
    public final boolean hasTimerQuery;

    public final String renderer;
    public final String vendor;
    public final String version;

    private MercurizerCapabilities(
            boolean isVulkan,
            int totalVramMb, int freeVramMb,
            int maxUniformBlockSize, int maxVertexAttribs, int maxVertexUniformBlocks,
            boolean hasDirectStateAccess, boolean hasMultiDrawIndirect, boolean hasBufferStorage,
            int maxTextureSize, int maxTextureImageUnits, int maxArrayTextureLayers,
            boolean hasTextureStorage, boolean hasAnisotropicFiltering, float maxAnisotropy,
            boolean hasS3tcCompression, boolean hasRgtcCompression,
            int maxComputeWorkGroupSizeX, boolean hasShaderStorageBufferObject, int maxFragmentUniformComponents,
            boolean hasSyncObjects, boolean hasTimerQuery,
            String renderer, String vendor, String version) {
        this.isVulkan = isVulkan;
        this.totalVramMb = totalVramMb;
        this.freeVramMb = freeVramMb;
        this.maxUniformBlockSize = maxUniformBlockSize;
        this.maxVertexAttribs = maxVertexAttribs;
        this.maxVertexUniformBlocks = maxVertexUniformBlocks;
        this.hasDirectStateAccess = hasDirectStateAccess;
        this.hasMultiDrawIndirect = hasMultiDrawIndirect;
        this.hasBufferStorage = hasBufferStorage;
        this.maxTextureSize = maxTextureSize;
        this.maxTextureImageUnits = maxTextureImageUnits;
        this.maxArrayTextureLayers = maxArrayTextureLayers;
        this.hasTextureStorage = hasTextureStorage;
        this.hasAnisotropicFiltering = hasAnisotropicFiltering;
        this.maxAnisotropy = maxAnisotropy;
        this.hasS3tcCompression = hasS3tcCompression;
        this.hasRgtcCompression = hasRgtcCompression;
        this.maxComputeWorkGroupSizeX = maxComputeWorkGroupSizeX;
        this.hasShaderStorageBufferObject = hasShaderStorageBufferObject;
        this.maxFragmentUniformComponents = maxFragmentUniformComponents;
        this.hasSyncObjects = hasSyncObjects;
        this.hasTimerQuery = hasTimerQuery;
        this.renderer = renderer != null ? renderer : "Unknown";
        this.vendor = vendor != null ? vendor : "Unknown";
        this.version = version != null ? version : "Unknown";
    }

    public static MercurizerCapabilities getCached() {
        return cached;
    }

    public static void clearCache() { cached = null; }

    public static MercurizerCapabilities probeAndCache() {
        if (cached != null) return cached;
        synchronized (MercurizerCapabilities.class) {
            if (cached != null) return cached;
            cached = isOpenGLContextAvailable() ? probe() : probeVulkan();
        }
        return cached;
    }

    private static boolean isOpenGLContextAvailable() {
        try {
            GL.getCapabilities();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static MercurizerCapabilities probeVulkan() {
        return new MercurizerCapabilities(
                true,
                -1, -1,
                0, 0, 0,
                false, false, false,
                0, 0, 0,
                false, false, 0f,
                false, false,
                0, false, 0,
                false, false,
                "Vulkan", "Unknown", "Vulkan");
    }

    private static MercurizerCapabilities probe() {
        Set<String> extensions = getExtensions();

        int totalVram = -1;
        int freeVram = -1;
        if (extensions.contains("GL_NVX_gpu_memory_info")) {
            try { totalVram = GL11.glGetInteger(GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX) / 1024; } catch (Exception ignored) {}
        }
        if (extensions.contains("GL_ATI_meminfo")) {
            try { freeVram = GL11.glGetInteger(GL_TEXTURE_FREE_MEMORY_ATI) / 1024; } catch (Exception ignored) {}
        }

        int maxUniformBlockSize = safeGetInt(GL31.GL_MAX_UNIFORM_BLOCK_SIZE);
        int maxVertexAttribs = safeGetInt(GL20.GL_MAX_VERTEX_ATTRIBS);
        int maxVertexUniformBlocks = safeGetInt(GL31.GL_MAX_VERTEX_UNIFORM_BLOCKS);
        boolean hasDirectStateAccess = extensions.contains("GL_ARB_direct_state_access");
        boolean hasMultiDrawIndirect = extensions.contains("GL_ARB_multi_draw_indirect");
        boolean hasBufferStorage = extensions.contains("GL_ARB_buffer_storage");

        int maxTextureSize = safeGetInt(GL11.GL_MAX_TEXTURE_SIZE);
        int maxTextureImageUnits = safeGetInt(GL20.GL_MAX_TEXTURE_IMAGE_UNITS);
        int maxArrayTextureLayers = safeGetInt(GL30.GL_MAX_ARRAY_TEXTURE_LAYERS);
        boolean hasTextureStorage = extensions.contains("GL_ARB_texture_storage");
        boolean hasAnisotropic = extensions.contains("GL_EXT_texture_filter_anisotropic");
        float maxAnisotropy = 0f;
        if (hasAnisotropic) {
            try { maxAnisotropy = GL11.glGetFloat(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT); } catch (Exception ignored) {}
        }
        boolean hasS3tc = extensions.contains("GL_EXT_texture_compression_s3tc");
        boolean hasRgtc = extensions.contains("GL_EXT_texture_compression_rgtc") ||
                          extensions.contains("GL_ARB_texture_compression_rgtc");

        int maxComputeX = 0;
        if (glVersionAtLeast(4, 3) || extensions.contains("GL_ARB_compute_shader")) {
            try { maxComputeX = GL43.glGetIntegeri(GL43.GL_MAX_COMPUTE_WORK_GROUP_SIZE, 0); } catch (Exception ignored) {}
        }
        boolean hasSSBO = extensions.contains("GL_ARB_shader_storage_buffer_object");
        int maxFragUniform = safeGetInt(GL20.GL_MAX_FRAGMENT_UNIFORM_COMPONENTS);

        boolean hasSync = extensions.contains("GL_ARB_sync");
        boolean hasTimer = extensions.contains("GL_ARB_timer_query");

        String renderer = GL11.glGetString(GL11.GL_RENDERER);
        String vendor = GL11.glGetString(GL11.GL_VENDOR);
        String version = GL11.glGetString(GL11.GL_VERSION);

        return new MercurizerCapabilities(
                false,
                totalVram, freeVram,
                maxUniformBlockSize, maxVertexAttribs, maxVertexUniformBlocks,
                hasDirectStateAccess, hasMultiDrawIndirect, hasBufferStorage,
                maxTextureSize, maxTextureImageUnits, maxArrayTextureLayers,
                hasTextureStorage, hasAnisotropic, maxAnisotropy,
                hasS3tc, hasRgtc,
                maxComputeX, hasSSBO, maxFragUniform,
                hasSync, hasTimer,
                renderer, vendor, version);
    }

    private static Set<String> getExtensions() {
        Set<String> set = new HashSet<>();
        try {
            int count = GL11.glGetInteger(GL_NUM_EXTENSIONS);
            for (int i = 0; i < count; i++) {
                String ext = GL30.glGetStringi(GL11.GL_EXTENSIONS, i);
                if (ext != null) set.add(ext);
            }
        } catch (Exception ignored) {}
        return set;
    }

    private static int safeGetInt(int constant) {
        try { return GL11.glGetInteger(constant); } catch (Exception e) { return 0; }
    }

    private static boolean glVersionAtLeast(int major, int minor) {
        try {
            int maj = GL11.glGetInteger(GL_MAJOR_VERSION);
            int min = GL11.glGetInteger(GL_MINOR_VERSION);
            return maj > major || (maj == major && min >= minor);
        } catch (Exception e) { return false; }
    }
}
