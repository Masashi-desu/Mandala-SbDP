---
title: 概要と非目標
order: 1
description: E2E中心の生きたドキュメントが扱う範囲と、初期版が意図的に扱わない範囲
---
# 概要と非目標

Mandalaの中心はE2Eフローです。UI操作、Client API、Spring Endpoint、Application Service、Doma DAO、外部SQL、PostgreSQL Tableを縦につなぎ、各境界のEvidenceとConfidenceを表示します。人間が最初から完全なE2E仕様を書く必要はありません。route、component、API client、mapping、traceからAgentが候補を発見し、review対象を提示できます。

実際の縦方向の接続は、公開サンプルの[プロジェクト作成成功フロー](sample-ref:flow:project.create.success)で確認できます。

## 対象スタック

- Java 21、Spring Boot 3系、Spring MVCまたはWebFlux
- Doma 3、外部SQLおよびtemplate
- PostgreSQL、Flyway、`information_schema`、`pg_catalog`
- OpenTelemetry、OTLP JSON
- TypeScript、Playwright、Request Interception
- Gradle Kotlin DSL、GitHub Actions、GitHub Pages

## 相補性

技術的事実は実行時観測、framework解決値、DB実スキーマ、OpenAPI、静的解析、Agent推論の順に重み付けします。一方、業務目的は人間review済み説明を優先します。異なる情報源は上書きで潰さず、矛盾を`Conflict`として残します。

## 人間とAgent

Agentは発見、収集、暫定説明、diff、stale候補を作ります。人間は業務意図、設計判断、誤検知、受容する例外をreviewします。どちらか一方を常に正とするモデルではありません。自由記述はCustom HTMLへ保存し、自動生成領域とは別に再利用します。

## 非目標

初期版はJPA、Hibernate中心の解析、MyBatis、jOOQ、PostgreSQL以外のDB、全private methodのcall graph、本番常時監視を提供しません。Reflection、procedure内部、実行されていない動的分岐は完全には確定できず、UNKNOWNまたはINFERREDとして表示します。Adapter境界は維持されるため、forkで別stackを追加できます。
