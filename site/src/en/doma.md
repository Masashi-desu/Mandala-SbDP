---
title: Doma and SQL analysis
order: 6
description: Linking DAOs, external SQL, templates, dynamic conditions, and PostgreSQL syntax
---
# Doma and SQL analysis

The Doma Adapter analyzes `@Dao` interfaces, `@Select`, `@Insert`, `@Update`, `@Delete`, batch, script, and procedure annotations, then connects method signatures to Doma-convention external SQL paths.

## DAO to external SQL

```text
dao:com.example.ProjectDao#insert
  EXECUTES_SQL
sql:META-INF/com/example/ProjectDao/insert.sql
```

Annotation mode without a SQL file is distinguished. Overloads use parameter types as supporting identity, and missing SQL produces a warning.

The sample connects [`ProjectDao`](sample-ref:dao:io.github.mandala.sbdp.sample.database.dao.ProjectDao) to [`insert(ProjectEntity)`](sample-ref:dao:io.github.mandala.sbdp.sample.database.dao.ProjectDao%23insert%28ProjectEntity%29) and its external SQL.

## Templates

Doma `/*%if*/`, `/*%for*/`, bind, literal, and embedded variables are retained as template segments. Static candidates are analyzed when all branches cannot be determined. Embedded-variable values are never stored.

## PostgreSQL parser

JSqlParser AST extracts SELECT, INSERT, UPDATE, DELETE, MERGE, CTE, subquery, JOIN, RETURNING, function, and column information. Regex is not the semantic parser. Template preprocessing and failure locations are retained as Evidence.

## Runtime integration

Normalized static SQL is compared with JDBC Spans. Matches become OBSERVED; table or column differences become Conflicts. Bind values are normalized to `?`.

## Batch and procedures

Batch operations connect one DAO method to multiple executions. CRUD inferred inside procedures or functions is marked `indirect=true` and INFERRED when introspection is only partial.
