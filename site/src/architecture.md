---
title: アーキテクチャ
order: 3
description: Core、Adapter、Capture、Normalize、Merge、Reconcile、Renderの構成と更新フロー
---
# アーキテクチャ

Mandala SbDPはframework非依存モデル、統合Core、入力Adapter、Renderer、CLI、Starterを分離します。外部projectは必要なAdapterだけを差し替えられます。

repository上では製品実装を`platform/`、解析対象を`sample-app/`、利用側の解析workspaceを`mandala/`、公式文書を`site/`、ローカル検証基盤を`infra/local/`へ分離します。生成物やsample固有処理を製品moduleへ混在させません。

## モジュール

| Module | 責務 |
|---|---|
| `mandala-model` | immutable Node、Edge、Evidence、Diff |
| `mandala-core` | merge、reverse index、confidence、conflict、stale、impact |
| `mandala-spring` | source、Actuator Mapping、OpenAPI |
| `mandala-doma` | DAO、method、外部SQL、PostgreSQL parser |
| `mandala-postgres` | JDBC、information_schema、pg_catalog |
| `mandala-opentelemetry` | OTLP trace normalizerとmasking |
| `mandala-renderer` | static HTML、ER、CRUD、custom section、search |
| `mandala-cli` | refresh lifecycle、serve、verify、diff |
| `mandala-spring-boot-starter` | service/DAO境界のSpan補完 |
| `mandala-gradle-plugin` | Gradle task integration |

## Pipeline

```text
Capture / Source scan / Introspection
                 ↓
Adapter-specific records
                 ↓ Normalize
Documentation Graph fragments
                 ↓ Merge
evidence-aware canonical Graph
                 ↓ Reconcile
conflict · stale · unconnected · diff
                 ↓ Render
static pages · reverse links · search
```

UI CaptureはDBへ接続せず、Runtime Captureはbrowserから分離します。`HTTP Method + normalized path`をjoin keyにして、観測タイミングが異なる二つのGraphを後から統合します。

## FullとIncremental

Full Refreshは全sourceを再取得します。IncrementalはGit changed pathから影響AdapterとNodeを選択し、cache metadataのcommit、config hash、adapter versionが一致する場合だけ再利用します。安全性を証明できないmigration、config、schema version変更では自動的にFullへfallbackします。

## シリアライズ契約

GraphはcanonicalなID順JSONです。生成時刻やMap順序はsemantic diffから除外します。schema versionはGraph rootに置き、readerは同一majorの未知fieldを許容するmigration方針を取ります。

生成後の投影例は[プロジェクト作成成功フロー](sample-ref:flow:project.create.success)で確認できます。GitHub Pagesでは公式文書を`docs/`、同じ公開bundleの解析成果物を`sample/`配下へ配置します。
