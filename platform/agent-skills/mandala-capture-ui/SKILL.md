---
name: mandala-capture-ui
description: Generate and run deterministic Playwright UI documentation scenarios with intercepted APIs, screenshots, ARIA/DOM snapshots, client-call observations, and undefined-communication detection. Use for UI capture, screen-state updates, screenshot refreshes, or reconciling frontend routes with Spring endpoints without connecting to a database.
---

# Mandala Capture UI

## 目的

backend/DBへ接続せず、通常・Loading・0件・validation・権限不足・API error・Not Foundを決定論的に収集する。

## 入力と前提

`mandala.yml`の`source.frontend.root`、`playwright.scenarios`、discovery JSON、fixtureを入力にする。`npm install`と`npx playwright install chromium`を完了しておく。

## 手順

1. `npm run discover:ui`を実行し、新route/action/API候補を確認する。
2. 発見候補から不足stateのmock/scenarioを追加する。人間の完全E2E仕様を前提にしない。
3. viewport、locale、timezone、color scheme、時刻、random、role、feature flag、response、animation、fontを固定する。
4. `npm run capture:ui`を実行する。Locator、ARIA、`data-doc-ready`を同期条件にする。
5. request/response、navigation、Screenshot、ARIA/DOM、console error、undefined communicationを確認する。
6. `mandala capture-ui --import-only`でGraphへ取り込む。

## 出力とEvidence

Screenshotを`playwright.screenshots`、observation JSONを`playwright.observations`で指定したrepository内の出力先へ保存する。実行結果は`PLAYWRIGHT_OBSERVATION`かつ`OBSERVED`、sourceから推定した未実行stateは`AGENT_INFERENCE`かつ`INFERRED`にする。別projectを扱う場合は設定値を使い、capture runner本体へproject固有pathを追加しない。

## 失敗時

未定義APIをnetworkへ通さずstatus 599で失敗させる。固定sleepでflaky testを隠さない。console error、missing locator、stability timeoutをscenario IDとともに残す。

## 編集境界と禁止事項

fixture、scenario、frontendのdoc readiness、mask設定は編集してよい。生成Screenshotを画像editorで修正しない。Authorization、Cookie、password、token、個人情報を保存しない。Custom HTMLと人間reviewを上書きしない。
