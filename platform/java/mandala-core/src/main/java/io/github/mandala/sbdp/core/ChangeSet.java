package io.github.mandala.sbdp.core;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record ChangeSet(List<ChangedFile> files) {
    public ChangeSet {
        files = files == null ? List.of() : files.stream().distinct().sorted().toList();
    }

    public static ChangeSet empty() {
        return new ChangeSet(List.of());
    }

    public static ChangeSet ofPaths(Collection<String> paths) {
        return new ChangeSet(paths.stream().map(ChangedFile::of).toList());
    }

    public Set<ChangeCategory> categories() {
        if (files.isEmpty()) return Set.of();
        EnumSet<ChangeCategory> result = EnumSet.noneOf(ChangeCategory.class);
        files.forEach(file -> {
            result.add(file.category());
            if (file.type() == FileChangeType.RENAMED) result.add(file.previousCategory());
        });
        return Set.copyOf(result);
    }

    public boolean intersects(Set<ChangeCategory> supported) {
        return supported == null || supported.isEmpty() || categories().stream().anyMatch(supported::contains);
    }
}
