package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.DocumentationGraph;

import java.util.Set;

/** Adapter SPI. Incremental results replace the adapter's complete previous fragment, so deletions are representable. */
public interface GraphAdapter {
    String name();

    String version();

    DocumentationGraph analyze(RefreshContext context) throws Exception;

    default Set<ChangeCategory> changeCategories() {
        return Set.of();
    }

    default boolean supportsIncremental() {
        return false;
    }

    default DocumentationGraph analyzeIncremental(RefreshContext context, DocumentationGraph previousFragment,
                                                  ChangeSet changes) throws Exception {
        throw new UnsupportedOperationException(name() + " does not support incremental analysis");
    }

    default boolean affectedBy(ChangeSet changes) {
        return changes.intersects(changeCategories());
    }
}
