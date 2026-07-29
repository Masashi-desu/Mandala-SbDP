package io.github.mandala.sbdp.doma;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import io.github.mandala.sbdp.doma.sql.PostgresSqlAnalyzer;
import io.github.mandala.sbdp.doma.sql.SqlAnalysisException;
import io.github.mandala.sbdp.doma.sql.SqlStatementAnalysis;
import io.github.mandala.sbdp.doma.sql.TableReference;
import io.github.mandala.sbdp.doma.sql.ColumnReference;
import io.github.mandala.sbdp.doma.sql.JoinReference;
import io.github.mandala.sbdp.doma.sql.CrudOperation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Discovers Doma DAO declarations and associates convention-based external SQL resources. */
public final class DomaSourceAnalyzer {
    private static final Map<String, DomaOperation> OPERATIONS = Map.ofEntries(
            Map.entry("Select", DomaOperation.SELECT),
            Map.entry("Insert", DomaOperation.INSERT),
            Map.entry("Update", DomaOperation.UPDATE),
            Map.entry("Delete", DomaOperation.DELETE),
            Map.entry("BatchInsert", DomaOperation.BATCH_INSERT),
            Map.entry("BatchUpdate", DomaOperation.BATCH_UPDATE),
            Map.entry("BatchDelete", DomaOperation.BATCH_DELETE),
            Map.entry("Script", DomaOperation.SCRIPT),
            Map.entry("Procedure", DomaOperation.PROCEDURE),
            Map.entry("Function", DomaOperation.FUNCTION),
            Map.entry("ArrayFactory", DomaOperation.ARRAY_CREATE));

    private final JavaParser parser;
    private final DomaSqlTemplateParser templateParser;
    private final PostgresSqlAnalyzer sqlAnalyzer;

    public DomaSourceAnalyzer() {
        this(new DomaSqlTemplateParser(), new PostgresSqlAnalyzer());
    }

    public DomaSourceAnalyzer(DomaSqlTemplateParser templateParser, PostgresSqlAnalyzer sqlAnalyzer) {
        this.templateParser = templateParser;
        this.sqlAnalyzer = sqlAnalyzer;
        this.parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
    }

    public DomaAnalysis analyze(Path javaSourceRoot, Path resourcesRoot) throws IOException {
        if (!Files.isDirectory(javaSourceRoot)) {
            throw new IllegalArgumentException("Java source root does not exist: " + javaSourceRoot);
        }
        if (!Files.isDirectory(resourcesRoot)) {
            throw new IllegalArgumentException("Resources root does not exist: " + resourcesRoot);
        }
        List<DomaDaoDescriptor> daos = new ArrayList<>();
        List<ExternalSqlMapping> mappings = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try (Stream<Path> files = Files.walk(javaSourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                ParseResult<CompilationUnit> parsed = parser.parse(file);
                if (parsed.getResult().isEmpty()) {
                    warnings.add(file + ": " + parsed.getProblems());
                    continue;
                }
                analyzeUnit(file, parsed.getResult().orElseThrow(), resourcesRoot, daos, mappings, warnings);
                parsed.getProblems().forEach(problem -> warnings.add(file + ": " + problem.getMessage()));
            }
        }
        daos.sort(Comparator.comparing(DomaDaoDescriptor::stableId));
        mappings.sort(Comparator.comparing(ExternalSqlMapping::stableId));
        return new DomaAnalysis(daos, mappings, warnings);
    }

    private void analyzeUnit(
            Path file,
            CompilationUnit unit,
            Path resourcesRoot,
            List<DomaDaoDescriptor> daos,
            List<ExternalSqlMapping> mappings,
            List<String> warnings) throws IOException {
        for (ClassOrInterfaceDeclaration declaration : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            Optional<AnnotationExpr> daoAnnotation = annotation(declaration.getAnnotations(), "Dao");
            if (daoAnnotation.isEmpty()) {
                continue;
            }
            String qualifiedName = qualifiedName(unit, declaration);
            List<DomaMethodDescriptor> methods = new ArrayList<>();
            for (MethodDeclaration method : declaration.getMethods()) {
                AnnotationExpr operationAnnotation = method.getAnnotations().stream()
                        .filter(annotation -> OPERATIONS.containsKey(simpleName(annotation)))
                        .findFirst()
                        .orElse(null);
                if (operationAnnotation == null) {
                    continue;
                }
                DomaOperation operation = OPERATIONS.get(simpleName(operationAnnotation));
                String methodId = "dao:" + qualifiedName + "#" + method.getNameAsString()
                        + method.getParameters().stream().map(this::canonicalParameterType)
                        .collect(java.util.stream.Collectors.joining(",", "(", ")"));
                boolean sqlFileDeclared = booleanAttribute(operationAnnotation, "sqlFile").orElse(false);
                Path external = locateSql(resourcesRoot, qualifiedName, method.getNameAsString(), operation);
                if (external != null) {
                    sqlFileDeclared = true;
                }
                if (sqlFileDeclared && external == null && operation != DomaOperation.PROCEDURE
                        && operation != DomaOperation.FUNCTION) {
                    warnings.add(methodId + ": external SQL was declared but no conventional resource exists");
                }
                Map<String, Object> attributes = new LinkedHashMap<>();
                attributes.put("annotation", operationAnnotation.toString());
                annotationAttributes(operationAnnotation).forEach(attributes::put);
                DomaMethodDescriptor descriptor = new DomaMethodDescriptor(
                        methodId,
                        qualifiedName,
                        method.getNameAsString(),
                        method.getTypeAsString(),
                        method.getParameters().stream()
                                .map(parameter -> new DaoParameter(
                                        parameter.getNameAsString(),
                                        parameter.getTypeAsString(),
                                        parameter.getAnnotations().stream().map(AnnotationExpr::toString).toList()))
                                .toList(),
                        operation,
                        sqlFileDeclared,
                        external,
                        javadocSummary(method),
                        position(file, method),
                        attributes);
                methods.add(descriptor);
                if (external != null) {
                    mappings.add(mapping(resourcesRoot, descriptor, external));
                }
            }
            daos.add(new DomaDaoDescriptor(
                    "dao:" + qualifiedName,
                    qualifiedName,
                    annotationAttribute(daoAnnotation.orElseThrow(), "config").isEmpty(),
                    methods,
                    javadocSummary(declaration),
                    position(file, declaration)));
        }
    }

    private ExternalSqlMapping mapping(Path resourcesRoot, DomaMethodDescriptor method, Path sqlFile)
            throws IOException {
        String source = Files.readString(sqlFile, StandardCharsets.UTF_8);
        DomaSqlTemplate template = templateParser.parse(source);
        List<List<SqlStatementAnalysis>> variants = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (int index = 0; index < template.parserVariants().size(); index++) {
            try {
                variants.add(sqlAnalyzer.analyze(template.parserVariants().get(index), template.dynamic()));
            } catch (SqlAnalysisException exception) {
                warnings.add("Template variant " + (index + 1) + ": " + exception.getMessage());
            }
        }
        List<SqlStatementAnalysis> statements = mergeVariants(variants, warnings);
        String relative = resourcesRoot.toAbsolutePath().normalize().relativize(sqlFile.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
        return new ExternalSqlMapping(
                "sql:" + relative,
                method.stableId(),
                sqlFile,
                template,
                statements,
                warnings);
    }

    private List<SqlStatementAnalysis> mergeVariants(
            List<List<SqlStatementAnalysis>> variants, List<String> warnings) {
        if (variants.isEmpty()) {
            return List.of();
        }
        int statementCount = variants.getFirst().size();
        if (variants.stream().anyMatch(variant -> variant.size() != statementCount)) {
            warnings.add("Dynamic template variants produced different statement counts; using the first count");
        }
        List<SqlStatementAnalysis> merged = new ArrayList<>();
        for (int statementIndex = 0; statementIndex < statementCount; statementIndex++) {
            List<SqlStatementAnalysis> candidates = new ArrayList<>();
            for (List<SqlStatementAnalysis> variant : variants) {
                if (statementIndex < variant.size()) {
                    candidates.add(variant.get(statementIndex));
                }
            }
            merged.add(mergeStatement(candidates));
        }
        return List.copyOf(merged);
    }

    private SqlStatementAnalysis mergeStatement(List<SqlStatementAnalysis> candidates) {
        SqlStatementAnalysis primary = candidates.getFirst();
        Map<String, TableReference> tables = new LinkedHashMap<>();
        for (SqlStatementAnalysis candidate : candidates) {
            for (TableReference table : candidate.tables()) {
                String key = table.schema() + '\u0000' + table.table() + '\u0000' + table.alias();
                tables.merge(key, table, (left, right) -> {
                    EnumSet<CrudOperation> operations = EnumSet.noneOf(CrudOperation.class);
                    operations.addAll(left.operations());
                    operations.addAll(right.operations());
                    return new TableReference(
                            left.schema(), left.table(), left.alias(), operations, left.directTarget() || right.directTarget());
                });
            }
        }
        LinkedHashSet<ColumnReference> columns = new LinkedHashSet<>();
        LinkedHashSet<JoinReference> joins = new LinkedHashSet<>();
        LinkedHashSet<String> ctes = new LinkedHashSet<>();
        LinkedHashSet<String> functions = new LinkedHashSet<>();
        LinkedHashSet<String> statementWarnings = new LinkedHashSet<>();
        boolean hasWhere = false;
        boolean hasSubquery = false;
        for (SqlStatementAnalysis candidate : candidates) {
            columns.addAll(candidate.columns());
            joins.addAll(candidate.joins());
            ctes.addAll(candidate.ctes());
            functions.addAll(candidate.functions());
            statementWarnings.addAll(candidate.warnings());
            hasWhere |= candidate.hasWhere();
            hasSubquery |= candidate.hasSubquery();
        }
        if (candidates.size() > 1) {
            statementWarnings.add("Merged references from " + candidates.size() + " Doma conditional variants");
        }
        return new SqlStatementAnalysis(
                primary.statementIndex(),
                primary.kind(),
                primary.normalizedSql(),
                List.copyOf(tables.values()),
                List.copyOf(columns),
                List.copyOf(joins),
                ctes,
                functions,
                hasWhere,
                hasSubquery,
                primary.dynamicTemplate(),
                List.copyOf(statementWarnings));
    }

    private Path locateSql(Path resourcesRoot, String qualifiedName, String method, DomaOperation operation) {
        String base = "META-INF/" + qualifiedName.replace('.', '/') + "/" + method;
        List<String> extensions = operation == DomaOperation.SCRIPT ? List.of(".script", ".sql") : List.of(".sql");
        for (String extension : extensions) {
            Path candidate = resourcesRoot.resolve(base + extension);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private Map<String, Object> annotationAttributes(AnnotationExpr annotation) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (annotation instanceof NormalAnnotationExpr normal) {
            normal.getPairs().forEach(pair -> result.put(pair.getNameAsString(), pair.getValue().toString()));
        } else if (annotation instanceof SingleMemberAnnotationExpr single) {
            result.put("value", single.getMemberValue().toString());
        }
        return result;
    }

    private Optional<Boolean> booleanAttribute(AnnotationExpr annotation, String name) {
        if (annotation instanceof NormalAnnotationExpr normal) {
            return normal.getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals(name))
                    .map(pair -> pair.getValue())
                    .filter(BooleanLiteralExpr.class::isInstance)
                    .map(BooleanLiteralExpr.class::cast)
                    .map(BooleanLiteralExpr::getValue)
                    .findFirst();
        }
        return Optional.empty();
    }

    private String annotationAttribute(AnnotationExpr annotation, String name) {
        if (annotation instanceof NormalAnnotationExpr normal) {
            return normal.getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals(name))
                    .map(pair -> pair.getValue().toString())
                    .findFirst()
                    .orElse("");
        }
        return "";
    }

    private Optional<AnnotationExpr> annotation(List<AnnotationExpr> annotations, String name) {
        return annotations.stream().filter(annotation -> simpleName(annotation).equals(name)).findFirst();
    }

    private String simpleName(AnnotationExpr annotation) {
        return annotation.getName().getIdentifier();
    }

    private String qualifiedName(CompilationUnit unit, ClassOrInterfaceDeclaration type) {
        List<String> names = new ArrayList<>();
        Node current = type;
        while (current instanceof ClassOrInterfaceDeclaration declaration) {
            names.addFirst(declaration.getNameAsString());
            current = declaration.getParentNode().orElse(null);
        }
        return unit.getPackageDeclaration().map(declaration -> declaration.getNameAsString() + ".").orElse("")
                + String.join(".", names);
    }

    private String canonicalParameterType(com.github.javaparser.ast.body.Parameter parameter) {
        String type = parameter.getTypeAsString().replaceAll("\\s+", "");
        return parameter.isVarArgs() ? type + "..." : type;
    }

    private DomaSourcePosition position(Path file, Node node) {
        var begin = node.getBegin().orElseThrow();
        return new DomaSourcePosition(file, begin.line, begin.column);
    }

    private String javadocSummary(Node node) {
        String text = node.getComment()
                .filter(comment -> comment.isJavadocComment())
                .map(comment -> comment.asJavadocComment().parse().getDescription().toText().trim())
                .orElse("");
        if (text.isBlank()) {
            return "";
        }
        int western = text.indexOf('.');
        int japanese = text.indexOf('。');
        int end = western < 0 ? japanese : japanese < 0 ? western : Math.min(western, japanese);
        return end < 0 ? text : text.substring(0, end + 1);
    }
}
