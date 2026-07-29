package io.github.mandala.sbdp.cli.pipeline;

import io.github.mandala.sbdp.cli.RepositoryContext;
import io.github.mandala.sbdp.core.ChangeCategory;
import io.github.mandala.sbdp.core.ChangeSet;
import io.github.mandala.sbdp.core.GraphAdapter;
import io.github.mandala.sbdp.core.RefreshContext;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.DocumentationGraphJson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

abstract class AbstractProjectAdapter implements GraphAdapter {
    protected final RepositoryContext repository;
    private final Set<ChangeCategory> categories;

    AbstractProjectAdapter(RepositoryContext repository, Set<ChangeCategory> categories) {
        this.repository = repository; this.categories = Set.copyOf(categories);
    }

    @Override public String version() { return "1.0.0"; }
    @Override public Set<ChangeCategory> changeCategories() { return categories; }
    @Override public boolean supportsIncremental() { return true; }
    @Override public DocumentationGraph analyzeIncremental(RefreshContext context, DocumentationGraph previous, ChangeSet changes) throws Exception { return analyze(context); }

    protected DocumentationGraph persist(DocumentationGraph graph) throws Exception {
        Path path = fragmentPath(); Files.createDirectories(path.getParent()); DocumentationGraphJson.write(path, graph); return graph;
    }

    Path fragmentPath() { return repository.resolve("mandala/cache/fragments/" + name() + ".json"); }
}
