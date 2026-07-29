---
title: SQL・CRUD解析
order: 10
description: SQL ASTとRuntime Observationからtable・column単位のCRUDを決定する規則
---
# SQL・CRUD解析

CRUDはHTTP methodから推測しません。`POST /search`はREAD、soft deleteはUPDATE、project createが`audit_logs`もCREATEするためです。SQL ASTを一次分類し、runtime traceで観測状態を付けます。

外部SQLの解析結果は[`ProjectDao/insert.sql`](sample-ref:sql:META-INF/io/github/mandala/sbdp/sample/database/dao/ProjectDao/insert.sql)、flowへの投影は[プロジェクト作成成功](sample-ref:flow:project.create.success)で確認できます。

## 基本分類

| SQL | CRUD |
|---|---|
| `SELECT` | READ |
| `INSERT` | CREATE |
| `UPDATE` | UPDATE |
| `DELETE` | DELETE |
| `MERGE` | CREATE / UPDATE |
| `TRUNCATE` | DELETE |

## Column

INSERT column list、UPDATE set、RETURNING、projection、WHERE、JOIN keyを役割別に抽出します。`SELECT *`は実スキーマと結合できた場合にcolumnへ展開し、できなければtable READとwarningを保持します。

## 論理削除

EndpointがDELETEでも、実行SQLが`UPDATE projects SET archived = true`なら`UPDATE public.projects`です。HTTP methodではなくSQL ASTの`UPDATE`として分類し、論理削除という設計意図はCustom HTMLやReview Evidenceで別に記録します。

## 直接と間接

applicationが実行したstatementはdirect、trigger/function/procedure内部はindirect、async consumer後続はasyncです。完全に展開できないfunctionはpartial evidenceとUNKNOWN/INFERREDを表示します。

## CRUD record

各recordはflow、endpoint、service、DAO/method、SQL、schema、table、column、operation、directness、Observed/Inferred、Evidence、Trace、Scenarioを持ちます。RendererはこれをE2E部分ER、table reverse lookup、CRUD matrixへ投影します。

[CRUD Matrix](sample/crud/)と[`public.audit_logs`](sample-ref:table:public.audit_logs)は、直接・間接の更新を成果物側から確認する入口です。
