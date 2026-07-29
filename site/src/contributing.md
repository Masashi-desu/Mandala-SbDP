---
title: コントリビューション
order: 20
description: 開発環境、module責務、coding規約、test、snapshot、Adapter、Pull Request、release
---
# コントリビューション

## 開発環境

Java 21、Node 24、Docker Engineを用意し、`./scripts/setup.sh`を実行します。変更前にrootの`GOAL.md`と`AGENTS.md`、対象moduleのtestを確認してください。

## Module責務

framework型を`mandala-model`へ持ち込まず、Adapter間を直接依存させません。CoreはEvidence-awareな統合だけを担当し、sample固有pathやtableを本体へhardcodeしません。生成fileを直接編集しません。

## Coding規約

Javaはimmutable record/value、null-safe constructor、repository-relative pathを基本とします。TypeScriptはstrict mode、unknownの検証、secret maskingを必須にします。errorをempty resultへ変換しません。主要機能にTODOや疑似実装を残しません。

## Tests

```bash
./gradlew test
npm test
./scripts/verify.sh
```

Stable ID、merge、reverse index、confidence、conflict/stale、diff、各Adapter、SQL/CRUD、Trace、Custom HTML、linkをUnit Testします。PostgreSQL、Flyway、Doma、SpringはIntegration Test、UIはPlaywrightで検証します。

## Snapshot

Golden更新は`./scripts/update-snapshots.sh`の明示操作だけで行います。diffをreviewし、意図しない変更を自動承認しません。Custom HTMLが保持されることも確認します。

## ドキュメント言語と原文

`site/src`の各Markdownには、同名の英語版を`site/src/en`へ追加します。欠落・余分な翻訳fileはbuild errorです。Stable ID、command、設定key、code、SQL、引用された用語、生成Graphのdataは翻訳せず、説明本文とRenderer所有のUI labelだけを翻訳します。

## Adapter追加

[拡張ガイド](extension.md)のcontractに従い、fixture、negative case、capability documentation、licenseを追加します。解析不能caseのEvidence/warningもtestします。

## Pull Request

PR workflowでJava、TypeScript、Playwright、integration、Full Refresh、snapshot、link、secret、Pages-ready bundleを検証します。Pages artifactには`site/dist`以外を含めず、サンプルの公開用静的投影は`site/dist/sample`だけへ配置します。

## GitHub Pages（本リポジトリ）

以下はMandala導入projectへの要件ではなく、このrepositoryの公式文書と検証済みサンプルを保守・公開するための規約です。

Pages artifactは`site/dist`だけから作ります。LPはrepository rootと`en/`、公式文書は`docs/<document-path>`と`docs/en/<document-path>`、検証済みの静的サンプルは`sample/<generated-artifact-path>`で公開します。workflowはartifact rootを明示し、repository全体をuploadしません。

## Release

schema compatibility、dependency license、NOTICE、Gradle plugin/Starter/CLI distribution、documentation versionを確認します。release artifactへlocal `.env`、raw trace、sample DB volumeを含めません。
