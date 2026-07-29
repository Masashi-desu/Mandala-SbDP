---
title: コア概念
order: 2
description: Node、Edge、Evidence、Confidence、Review、Conflict、Stale、Stable IDの意味
---
# コア概念

## Mandala

Mandalaは画面からDBまでの情報を同じrelation graphに置いた、再生成可能な技術ドキュメントです。HTMLそのものが正本ではなく、解析入力、Custom HTML、versioned Documentation GraphからRendererが生成します。

## NodeとEdge

Nodeは`SCREEN`、`HTTP_ENDPOINT`、`JAVA_METHOD`、`SQL_STATEMENT`、`DB_COLUMN`などの項目です。Edgeは`CALLS_HTTP`、`ROUTES_TO`、`EXECUTES_SQL`、`READS`、`UPDATES`などの関係です。逆方向Edgeを重複保存せず、`from`と`to`からreverse indexを構築します。

公開サンプルでは[プロジェクト作成Endpoint](sample-ref:endpoint:POST:/api/projects)から順方向を、[`public.projects` Table](sample-ref:table:public.projects)から逆方向を辿れます。

## Evidence

Evidenceは主張の由来です。`RUNTIME_OBSERVATION`、`SPRING_MAPPING`、`OPENAPI`、`SOURCE_CODE`、`JAVADOC`、`DOMA_MAPPING`、`SQL_STATIC_ANALYSIS`、`DATABASE_INTROSPECTION`、`PLAYWRIGHT_OBSERVATION`、`AGENT_INFERENCE`、`HUMAN_INPUT`を区別します。source location、commit、adapter、observed time、詳細を保持します。

## Confidence

| 値 | 意味 |
|---|---|
| `OBSERVED` | TraceやPlaywrightで実際に観測 |
| `DECLARED` | Mapping、OpenAPI、DB schemaに宣言 |
| `INFERRED` | 静的解析やAgent推論 |
| `HUMAN_REVIEWED` | 人間が確認済み |
| `CONFLICTED` | 情報源が矛盾 |
| `STALE` | 元実装の変更後に未確認 |
| `UNKNOWN` | 根拠不足 |

不確実な項目は色、border、labelで観測済みの項目と区別します。

## Review State、Conflict、Stale

Review Stateは未review、Agent review、人間review、却下などのworkflow状態です。Conflictは同一fieldに異なる主張がある状態で、証拠を両方保持します。Staleは説明生成時のsource fingerprintと現在値が異なる状態です。自動削除せず、review queueに残します。

## Stable ID

`endpoint:POST:/api/projects`や`column:public.projects.name`のようにsemantic identityから生成します。行番号、trace ID、解析時刻は主IDに使いません。同じ要素はcommitが変わっても同じIDとなり、意味のあるdiffを取れます。

この公式文書内のサンプルリンクもStable IDで記述し、site build時に`page-map.json`の実ファイル名へ解決します。未解決IDはbuild errorです。
