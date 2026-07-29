---
title: 第三者componentと画像
order: 22
description: 直接依存、build tool、container、配信画像のlicenseと配布境界
---
# 第三者componentと画像

Mandala SbDPのApache-2.0は、依存library、tool、container、画像をApache-2.0へ変更しません。各componentは自身のlicenseを維持します。直接採用するcomponentとassetの配布用台帳は[第三者台帳](../legal/THIRD_PARTY_NOTICES.txt)です。

## 製品・sampleの直接Java依存

| Component | Version | 採用license | 用途 |
|---|---:|---|---|
| Spring Boot / Framework / Security | 3.5.3 / 6.2.8 / 6.5.1 | Apache-2.0 | Starter連携、sample runtime |
| Doma / Doma Spring Boot | 3.9.1 / 2.4.0 | Apache-2.0 | Doma解析、sample永続化 |
| Jackson | 2.19.0（sample解決時2.19.1） | Apache-2.0 | JSON、YAML、Java time |
| JavaParser symbol solver | 3.27.0 | Apache-2.0 OR LGPL-3.0-or-laterのうちApache-2.0を選択 | Java source解析 |
| JSqlParser | 5.3 | Apache-2.0 OR LGPL-2.1-or-laterのうちApache-2.0を選択 | SQL解析 |
| picocli / SnakeYAML | 4.7.7 / 2.4 | Apache-2.0 | CLI、YAML |
| OpenTelemetry Java API / SDK / OTLP | 1.51.0 | Apache-2.0 | Trace計装・出力 |
| PostgreSQL JDBC | 42.7.7 | BSD-2-Clause | PostgreSQL接続 |
| Flyway | 11.8.2 | Apache-2.0 | sample migration |
| springdoc-openapi | 2.8.6 | Apache-2.0 | sample OpenAPI |
| AspectJ Weaver | 1.9.24 | EPL-2.0 | Starter AOP |
| Jakarta Servlet API | 6.1.0 | EPL-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0のうちEPL-2.0を選択 | compile-only境界 |
| JUnit / AssertJ | 5.12.2 / 3.27.3 | EPL-2.0 / Apache-2.0 | testのみ |

実際の宣言versionは`gradle/libs.versions.toml`と各`build.gradle.kts`、解決済みの推移依存はGradle dependency reportが正本です。

## Node.js、Docs、UI収集

| Component | Version | License | 用途 |
|---|---:|---|---|
| Playwright Test | 1.61.0 | Apache-2.0 | UI discovery／capture |
| TypeScript | 6.0.3 | Apache-2.0 | build／typecheck |
| markdown-it | 14.3.0 | MIT | 公式Docs生成 |
| js-yaml / tsx | 5.2.2 / 4.23.1 | MIT | capture設定／script実行 |
| Vite | 8.1.5 | MIT | sample frontend |
| Vitest / jsdom | 4.1.10 / 29.1.1 | MIT | testのみ |
| `@types/*`直接依存 | `package-lock.json`に固定 | MIT | buildのみ |

Node.js packageはPages artifactへcopyしません。直接・推移依存の正確な解決結果は`package-lock.json`で追跡します。

## Download toolとlocal container

| Component | Version | 主license | 配布境界 |
|---|---:|---|---|
| Gradle | 8.14.3 | Apache-2.0 | Wrapperを同梱し、distributionを取得 |
| Docker Compose | 2.39.1 | Apache-2.0 | pluginがない場合だけlocal取得 |
| OpenTelemetry Java agent | 2.16.0 | Apache-2.0 | checksum検証してlocal取得 |
| Playwright Chromium | Playwright 1.61.0指定revision | BSD-3-Clauseと同梱第三者notice | local取得 |
| PostgreSQL container | 16.9-alpine | PostgreSQL LicenseとAlpine packageの各license | local検証用 |
| Jaeger | 1.68.0 | Apache-2.0と同梱第三者notice | local検証用 |
| OpenTelemetry Collector Contrib | 0.128.0 | Apache-2.0と同梱第三者notice | local検証用 |
| Alpine helper | 3.22.0 | 含まれるpackageごとのlicense | local検証用 |

これらをrepositoryやPagesで再配布しません。containerとbrowserは複数componentを含む集合物なので、取得したbinary内のlicense／noticeが最終的な正本です。

## 配信画像

| 作品 | Local asset | Rights | 加工と表示 |
|---|---|---|---|
| *Chakrasamvara Mandala*、Nepal、ca. 1100、object 1995.233 | `site/assets/chakrasamvara-mandala.webp` | Public Domain / CC0-1.0 | The MetのOpen Access原画像をresizeし、WebPへ変換してLP背景に使用 |

- [The Metropolitan Museum of Artの作品ページ](https://www.metmuseum.org/art/collection/search/38021)
- [The Met Open Access policy](https://www.metmuseum.org/policies/image-resources)

CC0はcreditを必須としませんが、来歴を追跡できるよう作品名と作品ページへのlinkをLPと台帳に残します。The Metの推奨に従ったcitationであり、The MetによるMandala SbDPのendorsementを示すものではありません。

公式siteに第三者font、icon pack、remote JavaScriptは含めません。faviconとinterface iconはproject作成物です。

## 更新時の運用

依存またはassetを追加・更新するPRでは、version manifest、lockfile、[第三者台帳](../legal/THIRD_PARTY_NOTICES.txt)、このページを同時に更新します。Release前には推移依存と実際にbundleするarchiveの`LICENSE`／`NOTICE`を再確認し、source releaseにはMandalaの`LICENSE`、`NOTICE`、第三者台帳を含めます。
