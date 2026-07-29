---
name: mandala-analyze-db
description: Introspect a Flyway-migrated PostgreSQL database, parse Doma SQL with a PostgreSQL AST, classify direct and indirect CRUD, and generate table, column, constraint, trigger, function, RLS, ER, and reverse-lookup graph data. Use for schema refreshes, migration changes, CRUD discrepancies, or database impact analysis.
---

# Mandala Analyze DB

## 目的

Java Entityの推測ではなくPostgreSQL実体とSQL ASTからschema、CRUD、ERを生成する。

## 入力と前提

read-only DB接続環境変数、Flyway適用済みlocal/CI DB、Doma SQL root、Trace snapshotを入力にする。接続先が本番でないことを確認する。

## 手順

1. migrationを適用し、対象schema/versionを確認する。
2. `mandala analyze-db`でJDBC、`information_schema`、`pg_catalog`を取得する。
3. table、column、PK/FK/unique/check/index、sequence、view、enum/domain、trigger/function、RLS、commentを正規化する。
4. JSqlParserのASTでSQL、CTE、subquery、JOIN、WHERE、RETURNING、function、columnを解析する。
5. SELECT/INSERT/UPDATE/DELETE/MERGE/TRUNCATEをCRUD分類し、TraceでObservedを付ける。
6. direct、trigger/function indirect、asyncを区別し、全体/部分ERとreverse lookupを生成する。

## 出力とEvidence

schema snapshotを`mandala/snapshots/db/schema.json`へ保存する。DB実体は`DATABASE_INTROSPECTION`/`DECLARED`、SQL ASTは`SQL_STATIC_ANALYSIS`/`INFERRED`、Trace一致は`RUNTIME_OBSERVATION`/`OBSERVED`とする。

## 失敗時

権限不足、unsupported construct、dynamic SQL、catalog取得漏れをwarning/errorへ残す。正規表現だけでSQL semanticを確定しない。部分解析を完全解析として表示しない。

## 編集境界と禁止事項

migration、SQL、config、parser/fixtureは目的に沿って編集してよい。本番DB、write-capable capture user、hardcoded DB passwordを使わない。HTTP MethodからCRUDを決めない。生成table pageを直接編集しない。
