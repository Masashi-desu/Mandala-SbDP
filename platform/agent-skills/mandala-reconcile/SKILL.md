---
name: mandala-reconcile
description: Merge source, Javadoc, Spring Mapping, OpenAPI, Playwright, OpenTelemetry, Doma, SQL, PostgreSQL, custom HTML, and previous-graph evidence; detect conflicts, stale claims, unconnected or unused endpoints, schema mismatches, unresolved references, and insufficient confidence. Use after any capture or source change and before rendering or review.
---

# Mandala Reconcile

## 目的

複数情報源を上書きで潰さず統合し、矛盾、stale、gap、confidence不足をreview可能にする。

## 入力と前提

各Adapter fragment、UI/runtime/schema snapshot、previous graph、Custom HTML、current commitを入力にする。各入力のadapter versionとconfig hashを確認する。

## 手順

1. `mandala reconcile`を実行しStable ID単位でEvidenceをmergeする。
2. technical factはruntime、framework解決値、DB実体、OpenAPI、static、Agent推論のpriorityで評価する。
3. design intentはhuman reviewed、approved description、Javadoc、Agent推論の順で評価する。
4. 値の差をConflictとして両Evidence付きで保持する。
5. source fingerprint差をStaleにし、削除/変更のimpact E2Eをreverse indexで求める。
6. unconnected/unused Endpoint、OpenAPI差、SQL/schema差、Custom HTML矛盾、未解析経路をreportする。

## 出力とEvidence

canonical Documentation Graph、conflict/stale/diff/review reportを出力する。reconcile判断自体の根拠もAdapter、source、priorityとともに保持する。

## 失敗時

dangling Edge、duplicate Stable ID、schema major mismatchは停止する。矛盾を自動的に多数決解決しない。安全にincremental mergeできなければFull Refreshへfallbackする。

## 編集境界と禁止事項

review metadata、Custom HTMLへの追加候補、解析元の明白なbugは編集してよい。人間の説明を無断で削除しない。INFERREDをOBSERVEDへ昇格しない。generated JSONを手修正してverifyを通さない。
