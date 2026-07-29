package io.github.mandala.sbdp.core;

public record CacheRequirements(
        String targetCommit,
        String configurationHash,
        String adapterName,
        String adapterVersion
) {
    public CacheRequirements {
        targetCommit = targetCommit == null ? null : targetCommit.strip();
        configurationHash = configurationHash == null ? "" : configurationHash.strip();
        adapterName = adapterName == null ? "" : adapterName.strip();
        adapterVersion = adapterVersion == null ? "" : adapterVersion.strip();
        if (configurationHash.isBlank() || adapterName.isBlank() || adapterVersion.isBlank()) {
            throw new IllegalArgumentException("Cache configuration, adapter, and version requirements are required");
        }
    }

    public boolean accepts(CacheMetadata metadata) {
        return (targetCommit == null || targetCommit.equals(metadata.targetCommit()))
                && configurationHash.equals(metadata.configurationHash())
                && adapterName.equals(metadata.adapterName())
                && adapterVersion.equals(metadata.adapterVersion());
    }
}
