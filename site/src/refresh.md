---
title: 継続更新
order: 12
description: Full/Incremental Refresh、cache、semantic diff、stale、conflict、CI運用
---
# 継続更新

## Full Refresh

frontend source、Playwright、Spring Mapping、OpenAPI、Java/Javadoc、Doma/SQL、OpenTelemetry、PostgreSQL、Custom HTMLをすべて再取得します。baselineがない、schema/config/adapter versionが変わった、Git historyが利用できない場合もFullです。

## Incremental Refresh

Git diffをpath classifierへ渡します。Java変更はSpring/Doma/related symbol、SQL変更はstatement/DAO/table、migration変更は全DB、frontend/fixture/scenario変更はUI、Custom HTML変更は該当Stable IDだけをinvalidateします。Coreのimpact analysisで下流E2Eとreverse consumerを再renderします。

## Cache

schema snapshot、OpenAPI、Spring Mapping、Trace、UI observation、parsed SQL、Graph fragment、rendered assetをcacheできます。keyはtarget commitだけでなくcontent hash、config hash、adapter name/version、schema versionを含みます。不一致cacheは利用しません。

## Fallback

changed fileの分類不能、rename追跡失敗、migration、config、schema major変更時はIncrementalからFullへfallbackします。`fallbackToFull=false`なら中途半端な更新をせず明示的に失敗します。

## Semantic Diff

前回Graphを`mandala/cache/previous-graph.json`へ保存し、currentと比較します。JSON orderと解析時刻を無視し、screen/endpoint/request/response/SQL/CRUD/schemaの追加、削除、変更、影響E2Eをreportします。

## CI

PRではFull Refresh、snapshot diff、Java/TypeScript/Playwright/integration test、双方向link、secret、Pages-ready bundleを検証します。mainでは`site/dist`だけをPages artifactにし、rootのLP、`docs/`配下の公式文書、`sample/`配下の検証済み静的Mandalaを同時に公開します。raw Graph、raw Trace、DB snapshot、local configは配布しません。
