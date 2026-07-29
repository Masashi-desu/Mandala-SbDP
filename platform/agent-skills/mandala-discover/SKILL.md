---
name: mandala-discover
description: Discover analyzable frontend routes, screen states, UI actions, client APIs, Spring endpoints, application services, Doma DAOs, external SQL, database objects, and existing custom HTML. Use when onboarding a Spring Boot/Doma/PostgreSQL repository or when changed source may introduce or remove documentation boundaries.
---

# Mandala Discover

## 目的

解析可能な境界候補をsourceから再現可能に抽出し、未接続項目と後続capture候補を作る。

## 入力と前提

- repository root、`mandala/config/mandala.yml`、Git worktreeを入力にする。
- Java 21、Gradle Wrapper、Node.jsを利用可能にする。
- 先に`git status --short`を確認し、利用者の変更を保持する。

## 手順

1. `npm run discover:ui`を実行し、TypeScript ASTからroute、control、API callを抽出する。
2. `./gradlew :mandala-cli:run --args='--config mandala/config/mandala.yml discover'`を実行する。
3. 発見Graphを確認し、Client APIをHTTP method + normalized pathでEndpointへjoinする。
4. DAO methodとDoma規約の外部SQL、SQLとschema object候補を確認する。
5. `mandala/custom`を走査し、Stable ID参照を発見する。
6. 未接続Endpoint、未使用Endpoint、解析失敗、low confidenceをreview候補へ残す。

## 出力とEvidence

`mandala/snapshots/ui/discovery.json`とGraph fragmentを出力する。source由来は`SOURCE_CODE`、Javadocは`JAVADOC`、mapping snapshotは`SPRING_MAPPING`、Agent補足は`AGENT_INFERENCE`とし、観測していない内容を`OBSERVED`にしない。

## 失敗時

parser error、missing SQL、unknown routeを握り潰さずpathと理由をwarningへ記録する。入力rootが誤っている場合は停止し、空Graphを成功扱いしない。

## 編集境界と禁止事項

config、fixture、scenario候補、sourceの明白な解析bugは編集してよい。`mandala/generated`を直接編集しない。Custom HTMLを削除・全置換しない。password、token、cookie、bind値を保存しない。sample固有pathを本体Adapterへhardcodeしない。
