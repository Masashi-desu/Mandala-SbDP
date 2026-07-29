---
title: Agent Skills
order: 13
description: discover、capture、analyze、reconcile、refresh、review Skillの契約と編集境界
---
# Agent Skills

`platform/agent-skills`の各SkillはCLIの再現可能なcommandを組み合わせます。Skillだけが独自の解析結果を手作業で生成することはありません。

## 実行順

1. `mandala-discover`がroute、action、API、Endpoint、service、DAO、SQL、DB object、Custom HTMLを発見
2. `mandala-capture-ui`がmockとscenario候補を生成・実行
3. `mandala-capture-runtime`がbackend scenarioとTraceを取得
4. `mandala-analyze-db`が実schema、SQL、CRUD、ERを生成
5. `mandala-reconcile`がEvidenceをmergeしconflict/staleを検出
6. `mandala-refresh`がfull/incremental lifecycleを実行
7. `mandala-review`が人間/Agent向けreview packetを作成

## Evidence規則

Agentがsourceから推定した内容は`AGENT_INFERENCE`か`SOURCE_CODE` EvidenceでINFERREDです。command outputやScreenshotを確認せずOBSERVEDにしません。解析不能はwarningとして残します。

## 編集可能領域

Agentはconfig、fixture、scenario、Custom HTML、review metadata、source codeを目的に沿って編集できます。`mandala/generated`を直接直して問題を隠しません。人間が書いたCustom HTMLを全置換・削除しません。secret、raw token、cookie、bind値を追加しません。

## Review packet

推定flow、screen概要、業務目的候補、例外、service候補、CRUD、conflict、stale、diff、Custom HTML更新候補をStable ID単位で並べます。accept時はreview stateとEvidenceを更新し、単に表示文を上書きしません。
