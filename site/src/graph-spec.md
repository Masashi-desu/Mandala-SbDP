---
title: Documentation Graph仕様
order: 4
description: NodeとEdgeの型、metadata、serialization、reverse index、diffの規約
---
# Documentation Graph仕様

Graph rootは`schemaVersion`、`projectId`、`targetCommit`、`analyzedAt`、`nodes`、`edges`を持ちます。NodeとEdgeはStable ID順にcanonical化され、duplicate IDとdangling edgeはverify errorです。

## Node型

UIは`E2E_FLOW`、`UI_ENTRY`、`SCREEN`、`SCREEN_STATE`、`UI_ACTION`、`SCREENSHOT`。HTTPは`HTTP_CLIENT_CALL`、`HTTP_ENDPOINT`、`OPENAPI_OPERATION`、request/response schema。Javaはclass、method、controller、application service、Doma DAO。DataはSQL、schema、table、column、view、materialized view、function、trigger、policy。Runtimeはtraceとspanです。

## Edge型

UIは`HAS_STATE`、`HAS_ACTION`、`PERFORMED_ON`、`TRANSITIONS_TO`、`NAVIGATES_TO`、`CAPTURED_AS`、`CALLS_HTTP`。`PERFORMED_ON`は開始`SCREEN_STATE`から`UI_ACTION`、`TRANSITIONS_TO`は`UI_ACTION`から終了`SCREEN_STATE`を結びます。画面間の集約経路は`SCREEN`間の`NAVIGATES_TO`として保持します。HTTP/Javaは`MATCHES_OPERATION`、`ROUTES_TO`、`ACCEPTS`、`RETURNS`、`CALLS`、`EXECUTES`。Dataは`EXECUTES_SQL`、`READS`、`CREATES`、`UPDATES`、`DELETES`、`REFERENCES`、`FK_TO`、trigger/function relationです。provenanceは`OBSERVED_IN`、`DECLARED_BY`、`INFERRED_FROM`です。

## Metadata

各要素はEvidence list、source locations、target commit、analyzed time、adapter、Confidence、Review State、StaleInfo、Conflict list、warning、related trace、related scenarioを持ちます。attributeはAdapter固有のstructured dataに使いますが、共通semanticをattributeへ隠しません。

## 逆引きindex

Coreは`outgoing[id]`と`incoming[id]`を一度構築します。Rendererは同じEdgeから順方向と逆方向を出すため、重複relationの片側だけが古くなる問題を避けます。`CALLED_BY`のような明示的domain relationは意味が異なる場合だけ利用します。

## Diff形式

DiffはNode/Edgeごとに`ADDED`、`REMOVED`、`MODIFIED`を記録し、変更field、before/after fingerprint、影響Stable IDを含みます。timestamp、collection order、adapter内部debug値は除外します。削除Nodeはprevious graph側のmetadataを保持して影響を説明します。

## 後方互換性

major version変更はCLIが明示的migrationを要求します。minor追加fieldはdefaultで読み取れます。Stable ID規約の変更はalias tableを伴い、黙って大量のdelete/addへ変換しません。
