package net.caffeinemc.mods.sodium.client;

public final class MercurizerBenchmarkResult {
    public final double bufferUploadBandwidthMBps;
    public final double smallBufferUploadBandwidthMBps;
    public final double cpuThroughputMOpsPerSec;
    public final int availableProcessors;
    public final String rendererAtBenchmarkTime;
    public final String driverVersionAtBenchmarkTime;
    public final long benchmarkTimestamp;
    public final boolean isRefined;

    public MercurizerBenchmarkResult(
            double bufferUploadBandwidthMBps,
            double smallBufferUploadBandwidthMBps,
            double cpuThroughputMOpsPerSec,
            int availableProcessors,
            String rendererAtBenchmarkTime,
            String driverVersionAtBenchmarkTime,
            long benchmarkTimestamp,
            boolean isRefined) {
        this.bufferUploadBandwidthMBps = bufferUploadBandwidthMBps;
        this.smallBufferUploadBandwidthMBps = smallBufferUploadBandwidthMBps;
        this.cpuThroughputMOpsPerSec = cpuThroughputMOpsPerSec;
        this.availableProcessors = availableProcessors;
        this.rendererAtBenchmarkTime = rendererAtBenchmarkTime;
        this.driverVersionAtBenchmarkTime = driverVersionAtBenchmarkTime;
        this.benchmarkTimestamp = benchmarkTimestamp;
        this.isRefined = isRefined;
    }
}
