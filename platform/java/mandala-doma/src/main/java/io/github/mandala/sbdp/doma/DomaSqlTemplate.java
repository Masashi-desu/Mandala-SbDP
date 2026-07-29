package io.github.mandala.sbdp.doma;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public record DomaSqlTemplate(
        boolean dynamic,
        List<TemplateDirective> directives,
        Set<String> parameterExpressions,
        String parserReadySql,
        List<String> parserVariants) {
    public DomaSqlTemplate {
        directives = List.copyOf(directives == null ? List.of() : directives);
        parameterExpressions = parameterExpressions == null || parameterExpressions.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(parameterExpressions)));
        parserReadySql = parserReadySql == null ? "" : parserReadySql;
        parserVariants = List.copyOf(parserVariants == null || parserVariants.isEmpty()
                ? List.of(parserReadySql)
                : parserVariants);
    }

    public DomaSqlTemplate(
            boolean dynamic,
            List<TemplateDirective> directives,
            Set<String> parameterExpressions,
            String parserReadySql) {
        this(dynamic, directives, parameterExpressions, parserReadySql, List.of(parserReadySql));
    }
}
