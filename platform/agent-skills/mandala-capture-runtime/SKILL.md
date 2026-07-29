---
name: mandala-capture-runtime
description: Start or use a local Spring Boot/Doma/PostgreSQL environment, execute API scenarios, collect OpenTelemetry endpoint-to-database traces, normalize important boundaries, and mask sensitive attributes. Use when runtime paths, observed SQL/CRUD, async links, or static-versus-runtime differences need collection or refresh.
---

# Mandala Capture Runtime

## 目的

HTTP ServerからController、Application Service、Doma DAO、JDBC/PostgreSQLまでの実行経路を観測する。

## 入力と前提

local/CI専用PostgreSQL、Flyway適用済みschema、sample backend、OTel Collector、API scenarioを入力にする。`.env`をsourceし、raw secretをcommand lineへ表示しない。

## 手順

1. `./scripts/start.sh`を実行し、DB、Collector、backendのhealthを確認する。
2. `./scripts/capture-runtime.sh`でloginとCRUD/error scenarioを実行する。
3. OTLP JSONのtrace/span parent、link、HTTP、service、DAO、DB属性を取り込む。
4. `mandala.flow.id`、`symbol.id`、`layer`、`endpoint.id`、`dao.id`、`sql.id`を正規化する。
5. SQL bind値とsensitive attributeをmaskし、static SQLと照合する。
6. `mandala capture-runtime --import-only`でRuntime Graphを更新する。

## 出力とEvidence

sanitized traceを`mandala/traces`、runtime snapshotを`mandala/snapshots/runtime`へ出力する。実際のspanだけを`RUNTIME_OBSERVATION`/`OBSERVED`にし、未観測source経路はINFERREDのままにする。

## 失敗時

health failure、missing parent、broken trace context、collector export errorを明示する。traceなしを成功扱いしない。async correlationが不確実ならUNKNOWN warningを残す。

## 編集境界と禁止事項

local scenario、Starter instrumentation、mask rule、Collector設定を編集してよい。本番DBへ接続しない。全private methodをspan化しない。raw Authorization、Cookie、Token、session、bind値をcommitしない。生成Graphを手編集しない。
