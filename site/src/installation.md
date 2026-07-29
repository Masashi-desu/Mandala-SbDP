---
title: 導入ガイド
order: 14
description: 必要環境、Starter、Doma、PostgreSQL、OpenTelemetry、Playwright、初回と継続解析
---
# 導入ガイド

## 必要環境

Java 21、Node.js 24、Docker Engine、BashまたはPowerShell 7を用意します。GradleはWrapper、npm dependencyはlockfileを使うためglobal installは不要です。Docker Compose pluginがない環境ではscriptがsingle-container fallbackを使います。

## 新規clone

```bash
git clone <repository-url>
cd mandala-sbdp
./scripts/setup.sh
./scripts/start.sh
./scripts/refresh-mandala.sh
```

`.env.example`から`.env`が作られます。local sample credential以外をrepositoryへcommitしないでください。

## 外部Spring Boot project

Mandala modulesをMaven repositoryから追加し、Gradle plugin `io.github.mandala.sbdp`を適用します。Starterをruntime dependencyへ追加し、Application ServiceまたはDAOに`@MandalaSpan`を付けます。Actuator mappingsとOpenAPIはlocal/CIだけで公開し、network accessを制限します。

```kotlin
plugins { id("io.github.mandala.sbdp") version "0.1.0" }
dependencies { implementation("io.github.mandala.sbdp:mandala-spring-boot-starter:0.1.0") }
```

## Doma

Java root、resources root、`META-INF` SQL rootを設定します。annotation processorが生成したclassを解析対象へ含める必要はありません。外部SQL fileとDAO interfaceのsourceが必要です。

## PostgreSQLとFlyway

CIで一時PostgreSQLを起動し、applicationと同じmigrationを適用してからread-only schema captureを実行します。URL、username、passwordはenvironment変数名だけを`mandala.yml`へ書きます。

## OpenTelemetry

OTel Java Agentをsample/backendへ付け、OTLP/HTTPでCollectorへexportします。Starterはservice/DAO境界にMandala属性を加えます。raw Traceは`mandala/traces`に置き、Pages artifactから除外します。

## Playwright

frontend dev serverだけを起動し、APIはroute interceptionします。`npm run discover:ui`で候補を更新し、`npm run capture:ui`でScreenshot/observationを再生成します。

## 初回と継続解析

初回は`mandala refresh --mode full`、通常は`--mode incremental`です。生成後に`mandala verify`を必ず実行します。Custom HTMLは生成先ではなく`mandala/custom`へ追加します。
