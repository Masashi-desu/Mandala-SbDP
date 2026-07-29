package io.github.mandala.sbdp.core;

public record CachedValue(CacheMetadata metadata, byte[] content) {
    public CachedValue {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
