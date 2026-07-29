---
title: 生成ドキュメント仕様
order: 11
description: E2E、Endpoint、Java、DAO、SQL、Table、ER、CRUD、Custom HTML、検索とLink Validation
---
# 生成ドキュメント仕様

RendererはGraphからstatic HTMLを生成し、server-side runtimeを必要としません。Node pageはStable ID、説明、attributes、Evidence、Confidence、warning、conflict、順方向relation、reverse indexを共通表示します。

## Page種別

E2E/ScreenはScreenshot、state、action、Client API、request/response、execution path、CRUD、部分ER、Custom HTMLを表示します。Endpointはmapping、handler、validation、error、利用screenを表示します。Java/DAO/SQLはJavadoc、signature、caller/callee、normalized SQL、static/runtime差分を表示します。Tableは一般的なテーブル定義書としてschema、column、type、nullable、default、comment、constraint、index、参照、trigger、function、RLSと関連SQL・DAO・Application Serviceを先頭に表示し、直後に関連E2EとCRUD reverse lookupを独立した主要セクションとして表示します。

画面遷移ページは、E2Eで観測された`SCREEN`をスクリーンショット付きNodeとして一覧配置し、`NAVIGATES_TO`をresponsiveな線で結んだ画面接続マップを表示します。全体ページは俯瞰に専念し、1対1の遷移一覧や画面内状態を重複表示しません。

各SCREEN個別ページには、その画面の代表スクリーンショット、開始または終了画面となる1対1の遷移、`SCREEN_STATE → UI_ACTION → SCREEN_STATE`を移譲します。操作単位では順序、role、feature flag、outcome、関連HTTP statusを併記し、同じ開始状態・操作から異なる結果へ進む条件分岐を区別します。

成果物例:

- [E2E: プロジェクト作成成功](sample-ref:flow:project.create.success)
- [Endpoint: `POST /api/projects`](sample-ref:endpoint:POST:/api/projects)
- [Table: `public.projects`](sample-ref:table:public.projects)
- [観測済み画面遷移図](sample/screens/transitions.html)

## ERとCRUD Matrix

全体ERと部分ERは固定画像ではなく、table/columnを選択できるsemantic HTML cardです。FKの始点・終点カラムをresponsiveな線で結び、両端にcardinalityを表示します。カード上は関係把握に必要なPK、FK、Unique、参照先だけをpreviewし、全カラム表示はTable個別pageへ分離します。CRUD matrixはE2E×TableのcellにCREATE/READ/UPDATE/DELETEを表示し、E2E、SQL、Tableへ移動できます。Column pageも関連SQLとE2Eをreverse indexで辿れます。

[ER図](sample/er/)と[CRUD Matrix](sample/crud/)は公開サンプルでそのまま確認できます。

## Custom HTML

`mandala/custom/{entries,endpoints,symbols,tables}/<stable-slug>/*.html`を自動sectionの後へ挿入します。再生成はsourceを変更しません。`mandala-*-ref`はStable IDで生成page linkへ変換され、未解決refはverify errorです。

[プロジェクト作成フローへ挿入されたCustom HTML](sample-ref:custom-html:entries/project-create-success)は、自動生成領域と手書き説明が共存する例です。

任意JavaScript、inline event handler、`javascript:` URLはdefaultで除去します。CSSは`.custom-html`配下へscopeすることを推奨します。明示的opt-in時だけscriptを許可します。

生成物の既定配色は公式LPと同期しています。全体配色を変える場合だけ`mandala/custom/palette.css`の`--mandala-light-*` / `--mandala-dark-*`公開トークンを使います。Rendererはこのファイルを再生成で保持し、任意selectorやremote importを拒否します。各項目のCustom CSSは全体テーマへ漏れないようCustom HTML内へ自動的にscopeします。

## SearchとLink Validation

`search-index.json`はtitle、type、Stable ID、description、URLだけを含みます。Renderer後に全relative `href`を解決し、dangling link、Graphのmissing node、unresolved custom refを失敗にします。

## 表示言語、原文、テーマ

公式Docsと生成Mandalaは、ヘッダーの言語選択で日本語・英語の説明を切り替え、テーマ選択でSystem・Light・Darkを切り替えます。選択値はブラウザのlocal storageへ保存され、両方の成果物で共有されます。SystemはOSの`prefers-color-scheme`に従います。

翻訳するのは公式Docsの説明本文、またはRenderer自身が付けたnavigation・見出し・空状態などのUI説明だけです。解析元から取得したdisplay name、description、Javadoc、用語、引用、code、SQL、Stable ID、Evidenceは原文を維持し、翻訳catalogへ渡しません。このため、選択した説明言語と解析元の原文が一つのページに併記される場合があります。
