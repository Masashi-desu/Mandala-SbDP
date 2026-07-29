# Repository instructions

## 要求と境界

- `GOAL.md` を最上位の要求仕様として扱い、変更前後に該当する完成条件を照合する。
- `platform/` は外部projectへ導入するMandala SbDP製品本体、`mandala/` は利用側の解析workspace、`infra/local/` はsample検証専用インフラである。責務を相互に混在させない。
- `site/src/` はMandala SbDP本体の公式技術ドキュメント、`mandala/generated/sample-app/site/` はsample解析結果の公開元であり、ソース責務を混在させない。Pages Artifactは `site/dist` のみとし、build時に後者の公開用投影を `site/dist/sample/` へ再生成する。
- framework固有APIは各Adapter moduleに閉じ、`mandala-model` と `mandala-core` をSpring/Doma/PostgreSQLから独立させる。
- Node/EdgeのIDはsemanticで安定した値にし、行番号、timestamp、raw trace IDだけを主IDにしない。

## 実装と生成物

- TODO、空実装、固定dummyで主要機能を代替しない。sample専用分岐をlibrary本体へ入れない。
- `mandala/generated`、`mandala/snapshots`、`site/dist` を手編集しない。入力またはgeneratorを修正し、`scripts/refresh-mandala.*` / `scripts/build-site.*` から再生成する。
- PagesのLPはrootと`en/`、公式文書は`docs/<document-path>`と`docs/en/<document-path>`、sample Mandalaはrepository project root相対の`sample/<generated-artifact-path>`で公開する。repository名やdomainを固定せず相対linkを使い、raw Graph、raw Trace、DB snapshot、local configをArtifactへ含めない。
- 公式文書は`site/src`の日本語版と`site/src/en`の英語版を同名fileで同期する。生成Mandalaの翻訳対象はRenderer所有のnavigation・見出し・説明だけとし、解析元のdisplay name、description、Javadoc、用語、引用、code、SQL、Stable ID、Evidenceを翻訳または改変しない。
- 公式文書と生成Mandalaの言語・テーマ選択はheaderに配置し、`ja|en`と`system|light|dark`以外を永続化しない。外部翻訳serviceへ解析dataを送信しない。
- `mandala/custom` の人間編集領域を再生成で上書きしない。Custom HTMLのJavaScript禁止とStable ID参照検証を維持する。
- 依存は安定版を選び、version catalog、package manifest、container tagへ固定する。秘密値やlocal固有pathをtracked fileへ追加しない。

## 検証

- Java変更は `./gradlew check`、TypeScript変更は `npm test && npm run typecheck && npm run build` を実行する。
- pipeline、renderer、sample、設定、script変更は可能な限り `./scripts/verify.sh`（Windowsは `.\scripts\verify.ps1`）まで実行する。
- screenshotまたはbrowser UI検証を行う前に、global instructionで指定された `browser-noninvasive-verification` skillを選定gateとして読む。
- 失敗をskipや期待値緩和で隠さず原因を修正する。環境上未実行の場合は、実装済みの検証commandと具体的な制約を報告する。

## Security

- password、cookie、session、Authorization、token、API key、DB password、個人情報、SQL bind値をGraph、Trace、screenshot、logへ保存しない。
- DB解析はread-onlyを基本とし、接続秘密は設定値ではなく環境変数名で参照する。
- external deploy、push、releaseは依頼なしに実行しない。Pages workflow自体は保守してよいが、local作業からdispatchしない。
