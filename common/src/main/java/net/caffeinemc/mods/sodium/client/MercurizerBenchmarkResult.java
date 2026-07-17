package net.caffeinemc.mods.sodium.client;

public final class MercurizerBenchmarkResult {
    public final double bufferUploadBandwidthMBps;
    public final double smallBufferUploadBandwidthMBps;
    public final double cpuThroughputMOpsPerSec;
    public final int    availableProcessors;
    public final String rendererAtBenchmarkTime;
    public final String driverVersionAtBenchmarkTime;
    public final long   benchmarkTimestamp;
    public final boolean isRefined;
    // v2 fields — may be 0 / false if loaded from old JSON
    public final double  smallBufferRoundTripNs;
    public final double  benchmarkConfidenceScore;
    public final boolean isLowConfidence;
    public final int     largeBufferSizeBytes;
    public final int     smallBufferSizeBytes;

    public MercurizerBenchmarkResult(
            double bufferUploadBandwidthMBps,
            double smallBufferUploadBandwidthMBps,
            double cpuThroughputMOpsPerSec,
            int    availableProcessors,
            String rendererAtBenchmarkTime,
            String driverVersionAtBenchmarkTime,
            long   benchmarkTimestamp,
            boolean isRefined,
            double  smallBufferRoundTripNs,
            double  benchmarkConfidenceScore,
            boolean isLowConfidence,
            int     largeBufferSizeBytes,
            int     smallBufferSizeBytes) {
        this.bufferUploadBandwidthMBps      = bufferUploadBandwidthMBps;
        this.smallBufferUploadBandwidthMBps = smallBufferUploadBandwidthMBps;
        this.cpuThroughputMOpsPerSec        = cpuThroughputMOpsPerSec;
        this.availableProcessors            = availableProcessors;
        this.rendererAtBenchmarkTime        = rendererAtBenchmarkTime;
        this.driverVersionAtBenchmarkTime   = driverVersionAtBenchmarkTime;
        this.benchmarkTimestamp             = benchmarkTimestamp;
        this.isRefined                      = isRefined;
        this.smallBufferRoundTripNs         = smallBufferRoundTripNs;
        this.benchmarkConfidenceScore       = benchmarkConfidenceScore;
        this.isLowConfidence                = isLowConfidence;
        this.largeBufferSizeBytes           = largeBufferSizeBytes;
        this.smallBufferSizeBytes           = smallBufferSizeBytes;
    }
}
