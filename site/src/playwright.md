---
title: Playwright UI Capture
order: 9
description: API Mock、画面状態、安定化、Client API、ARIA、未定義通信を収集する仕組み
---
# Playwright UI Capture

UI Captureは通常のassertion中心E2Eではなく、画面仕様のdocument executionです。backendとDBへ接続せず、Request InterceptionとfixtureでAPI境界を固定します。Runtime CaptureとはHTTP methodとnormalized pathで後からjoinします。

## 自動発見

TypeScript ASTからroute declarationとAPI client callを発見し、template pathを`{id}`へ正規化します。template内のbutton、link、input、selectも候補として列挙し、routeごとの暫定flowをINFERREDとして出力します。人間が事前に完全なscenarioを書くことは必須ではありません。

Capture runnerはrepository root、frontend root、scenario glob、observation/Screenshot出力、base URL、dev serverを`mandala.yml`から解決します。同じ値を`MANDALA_CAPTURE_*`環境変数または`npm run capture:ui -- --frontend-root ... --scenario ...`のCLI optionで上書きでき、CLI、環境変数、設定ファイルの順に優先されます。runnerがdev serverを管理する場合も`reuseExistingServer`を設定値として扱うため、CIが先に起動したserverとport競合しません。

## 画面状態

通常、Loading、0件、入力値error、権限不足、API error、Not Foundを扱います。sample scenarioは発見結果をreviewしたfixtureですが、capture runner自体はYAMLを読み込む再利用可能なengineです。YAML fileの`defaults.environment`でroleとfeature flagの共通値を宣言し、各scenarioの`environment`で上書きできます。未定義API通信はstatus 599で失敗させ、黙ってnetworkへ通しません。

公開成果物で各状態を確認できます。

- [成功](sample-ref:flow:project.create.success) / [入力値error](sample-ref:flow:project.create.validation)
- [0件](sample-ref:flow:projects.empty) / [Loading](sample-ref:flow:projects.loading)
- [権限不足](sample-ref:flow:forbidden) / [API error](sample-ref:flow:api.error) / [Not Found](sample-ref:flow:not.found)

## 決定論

viewport 1440×1000、`ja-JP`、Asia/Tokyo、light color scheme、device scale factor 1、固定日時、固定random、role、response、reduced motion、caret、font loadingを固定します。`body[data-doc-ready=true]`、ARIA、locatorを同期条件とし、固定時間sleepを主要同期に使いません。

## 収集形式

route、URL、state、action、locator、request/response status、normalized path、masked body、mock ID、navigation、screenshot、ARIA snapshot、DOM text、console error、undefined communicationをJSONへ保存します。URL pathではresource名に依存せず、数字だけのsegmentを`{id}`へ正規化します。password、cookie、Authorization、token、個人情報は再帰maskします。

Observation schema `1.1`では、各操作を`transitions[]`として記録します。各recordは`sequence`、`from { route, state }`、sanitized `action`、`to { route, state }`、条件となるrole・feature flag・操作直後の`outcome`・scenario全体の`scenarioOutcome`、その操作中に発生した`relatedHttp`を持ちます。HTTP observationにも`actionSequence`を付けるため、複数操作を含むscenarioでも初期画面loadや別操作の通信を最後の操作へ誤って集約しません。

各`from`/`to`にはその遷移時点のprimary headingを原文の`name`として、repository相対`screenshot`と併せて記録します。これにより最終結果だけでなく、入力・編集・作成など遷移元の画面も正しい画面名と画像でSCREEN個別資料と全体の画面接続マップに表示できます。

state名は`body[data-mandala-state]`または`body[data-doc-state]`を優先し、未指定時はscenarioの`initialState`、各actionの`resultState`、最終`state`を使用します。これにより、URLが変わらないnormal→validation-errorなども画面状態遷移として保存できます。

## Screenshotのreview

ScreenshotはGraphの`SCREENSHOT` Nodeとなり、screen stateへ`CAPTURED_AS`で接続します。visual差分は明示的なGolden更新commandでだけ承認し、CIが自動更新しません。
