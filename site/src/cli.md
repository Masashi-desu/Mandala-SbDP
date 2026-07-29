---
title: CLIリファレンス
order: 15
description: 全mandala command、主要option、終了code、利用例
---
# CLIリファレンス

共通optionは`--repository <path>`と`--config <path>`です。default configは`mandala/config/mandala.yml`です。

## Commands

| Command | 処理 |
|---|---|
| `mandala init` | config、directory、Custom HTML templateを非破壊作成 |
| `mandala discover` | route、client API、Spring、Java、Doma、SQLを発見 |
| `mandala capture-ui` | Playwrightを実行しUI observationをimport |
| `mandala capture-runtime` | API scenarioを実行しOTel Traceをimport |
| `mandala analyze-db` | PostgreSQL実schemaとSQL CRUDを解析 |
| `mandala reconcile` | Graph merge、confidence、conflict、staleを更新 |
| `mandala refresh` | captureからrender/verifyまで一括実行 |
| `mandala render` | 保存GraphからHTMLだけを再生成 |
| `mandala verify` | Graph、link、custom ref、secret、生成差分を検証 |
| `mandala diff` | previous/currentのsemantic diffを表示 |
| `mandala serve` | static Mandalaまたは指定した公開bundleをlocalhostで配信 |

## 主要option

`init --force-config`は既存configをbackupしてtemplateを更新します。`capture-ui --import-only`と`capture-runtime --import-only`は外部commandを起動しません。`analyze-db --snapshot-only`はDBへ接続せず既存snapshotを使います。

`refresh --mode full|incremental --offline`を指定できます。offlineはsource、既存snapshot/observation/traceだけで再構築します。`verify --strict-review`は未解決conflict/staleも失敗にします。`diff --fail-on-change`はsemantic changeでexit 4です。`serve --bind 127.0.0.1 --port 4174`は設定の`mandala.output.site`をdefaultで外部公開せず配信します。`serve --root site/dist`のようにrepository相対rootを明示すると、Pages-ready bundleを配信できます。

## 終了code

| Code | 意味 |
|---:|---|
| 0 | 成功 |
| 2 | usageまたはconfig不正 |
| 3 | 解析・I/O失敗 |
| 4 | verify、strict review、fail-on-change |
| 5 | Playwright、runtime scenario、DBなど外部capture失敗 |

## Examples

```bash
./gradlew :mandala-cli:installDist
./platform/java/mandala-cli/build/install/mandala/bin/mandala --config mandala/config/mandala.yml refresh --mode full
./gradlew mandalaRefresh
./scripts/serve-mandala.sh --port 4174
```

wrapper scriptは`site/dist`を再生成してLPを`/`、Docsを`/docs/`、サンプルMandalaを`/sample/`で配信します。CLIはerrorを握り潰しません。fallbackした場合もreasonを標準出力とcache manifestへ記録します。
