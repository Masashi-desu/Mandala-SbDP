---
title: OpenTelemetry統合
order: 8
description: EndpointからDBまでの実行経路、独自属性、Trace取込、機密情報の扱い
---
# OpenTelemetry統合

Runtime GraphはOpenTelemetryのtrace/spanをOTLP JSONから取り込みます。自動計装のHTTP serverとJDBC spanに、StarterのAOPでApplication ServiceとDoma DAO境界を補完します。private methodを一律span化しません。

## 識別する境界

- HTTP server、Controller、Application Service、Use Case
- Doma DAO、JDBC/R2DBC、外部HTTP client
- async task、consumer、message processing

親子関係とlinkを維持し、async boundaryではtrace contextまたはMandala flow属性で関連付けます。

## Mandala属性

`mandala.flow.id`、`mandala.symbol.id`、`mandala.layer`、`mandala.endpoint.id`、`mandala.dao.id`、`mandala.sql.id`を使用します。Starter annotationがStable IDを明示でき、未指定時はclass/methodから決定論的に生成します。

## SQL

`db.system=postgresql`、`db.operation.name`、sanitized statementを取り込みます。bind値、connection stringのpassword、user dataは保存しません。同じnormalized SQLをDoma外部SQLへ接続し、静的に存在する経路と実際に観測された経路を別Evidenceで表示します。

## Masking

取込時は`security.maskKeys`をsecure defaultへ追加し、Authorization、Cookie、Token、session、email、SQL literalを大文字小文字を無視して再帰的にmaskします。さらにGraphへ渡す境界ではallowlistを適用し、HTTP route/method、DB operation/sanitized SQL、Java symbol、messaging/RPC、`mandala.*`など関連付けに必要な属性だけを保持します。

resource属性は`service.name`、namespace/version、deployment environment、telemetry SDK情報だけを保持します。`host.name`、`process.*`、`service.instance.id`、request/response header、status messageは保存せず、allowlist内に現れたローカルabsolute pathもredactします。raw traceはPages artifactへ含めません。`mandala verify`は生成物を再scanします。

## 観測の意味

OBSERVEDは「用意したscenarioでその経路を通った」ことを示し、全入力で必ず同じ経路になることを意味しません。未観測branchは削除せず、source由来INFERRED/DECLAREDとして残します。

[プロジェクト作成成功フロー](sample-ref:flow:project.create.success)では、静的に発見した経路とruntimeで観測した経路を同じページで比較できます。
