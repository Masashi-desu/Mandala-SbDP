---
title: Mandala SbDP
order: 0
description: 画面からPostgreSQLまでをEvidence付きで双方向接続する、生きた技術ドキュメント基盤
---
# 実装を、辿れる構造に。

Mandala SbDPは、画面からHTTP、Java、SQL、PostgreSQLまでを一つのDocumentation Graphに接続します。

散在する設計情報を、EvidenceとStable IDを備えた「生きた技術ドキュメント」へ。

- [ドキュメントを読む](overview.md)
- [サンプルMandalaを探索する](sample/)

## 画面からスキーマまで、同じGraphを辿る

静的解析、宣言、実行時観測、DB実態を相補的に統合。順方向だけでなく、Columnから利用画面へも同じEdgeを逆引きできます。

- Screen / E2E
- HTTP / Trace
- Java / Doma
- SQL / CRUD
- PostgreSQL

## 知りたいところから読む

すべてを順番に読む必要はありません。導入、概念、日々の更新から、いま必要な入口を選べます。

- [全体像と非目標](overview.md) — Mandalaが接続するものと、初期版の境界
- [導入ガイド](installation.md) — 外部Spring Bootプロジェクトへの組み込み
- [Documentation Graph](concepts.md) — Node、Edge、Evidence、Confidenceの考え方
- [更新と差分](refresh.md) — Full / Incremental Refreshと継続運用

## まず、動いているMandalaを見る

サンプルアプリケーションを実際に解析した成果物です。画面からEndpoint、Java、SQL、Tableへ進み、同じ関係を逆向きにも辿れます。

- [ER図を見る](sample/er/)
- [画面遷移図を見る](sample/screens/transitions.html)
- [CRUDマトリクスを見る](sample/crud/)
