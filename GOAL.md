# Mandala SbDP — GOAL.md

## 1. 目的

本リポジトリでは、**Mandala SbDP** の実用的なOSS参照実装をワンショットで構築する。

Mandala SbDPは、Spring Boot・Doma・PostgreSQLで構成されたアプリケーションを対象に、画面、API、アプリケーション実行経路、Javaシンボル、Doma DAO、SQL、CRUD、テーブル定義、ER図を双方向に接続する、E2E中心のドキュメント生成ライブラリである。

本リポジトリの主要成果物はサンプルアプリケーションではない。

主要成果物は、外部のSpring Boot・Doma・PostgreSQLアプリケーションへ導入し、継続的にMandalaを生成・更新できる次の仕組みである。

* Javaライブラリ
* Spring Boot Starter
* CLI
* Gradle連携
* 解析パイプライン
* PlaywrightによるUI収集
* OpenTelemetryによる実行経路収集
* PostgreSQL実スキーマ収集
* Documentation Graph
* 静的ドキュメント生成器
* Agent Skills
* 継続更新・差分検出機構

サンプルアプリケーションは、Mandala SbDPの導入方法、解析能力、生成結果、継続更新、双方向リンクを検証するための解析対象として同梱する。

---

## 2. Mandalaの定義

本プロジェクトにおけるMandalaとは、アプリケーションを構成する次の要素を、一つの関係グラフとして表現した生きた技術ドキュメントである。

```text
画面・画面状態
    ↓
UI操作
    ↓
クライアントAPI
    ↓
HTTPエンドポイント
    ↓
Controller
    ↓
Application Service
    ↓
Doma DAO
    ↓
SQL
    ↓
CRUD
    ↓
テーブル・カラム
```

Mandalaは一方向のドキュメントではない。

DBテーブルやカラムから、そのDBオブジェクトを利用するSQL、DAO、Application Service、Endpoint、E2Eフロー、画面へ逆方向にも辿れること。

```text
カラム・テーブル
    ↓
関連SQL
    ↓
Doma DAO
    ↓
Application Service
    ↓
HTTPエンドポイント
    ↓
E2Eフロー
    ↓
画面
```

---

## 3. 基本思想

Mandala SbDPは、一度だけ解析して固定的な設計書を出力するツールではない。

コード、画面、通信、実行結果、SQL、DB構造、コメント、既存文書、Agentの推論、人間のレビューを継続的に統合する、**相補性のある生きたドキュメント**を目指す。

以下を必達原則とする。

1. 人間が最初からE2E仕様を記述することを前提にしない。
2. Agentがコード、画面、通信、型、コメント、Trace、DB構造から暫定仕様を構築できること。
3. 人間とAgentのどちらも、生成された仕様をレビュー、補足、修正できること。
4. 人間の記述を常に正とせず、実装や観測結果との矛盾を検出できること。
5. コードを唯一の正本とせず、宣言、実行時観測、DB実態、補足説明を統合すること。
6. 自動生成部分と自由記述部分を分離し、再生成によって自由記述を破壊しないこと。
7. 解析情報に、情報源、根拠、確度、対象コミット、解析日時を保持すること。
8. 解析不能な内容を推測だけで確定扱いしないこと。
9. 実装上存在する経路と、実際に観測された経路を区別すること。
10. 正常系だけでなく、エラー、権限、空状態などの画面状態を扱えること。
11. 生成物を手動編集して完成させず、再生成可能な仕組みを完成させること。
12. 一度きりの解析ではなく、Full RefreshとIncremental Refreshを継続実行できること。
13. コード変更による仕様差分、影響範囲、stale、conflictを検出できること。
14. 人間とAgentの役割を固定せず、双方が仕様形成へ参加できること。

---

## 4. 初期対応範囲

初期参照実装は、以下の技術構成へ明確に限定する。

* Java 21
* Spring Boot
* Spring MVCまたはSpring WebFlux
* Doma
* PostgreSQL
* Flyway
* OpenTelemetry
* Playwright
* Gradle Kotlin DSL
* TypeScript
* GitHub Actions
* GitHub Pages

安定版の依存関係を選択し、バージョンを明示的に固定する。

以下は初期版の完成条件に含めない。

* JPA
* Hibernate中心の永続化解析
* MyBatis
* jOOQ
* MySQL
* Oracle Database
* SQL Server
* Node.jsバックエンド
* .NET
* Pythonバックエンド
* すべてのJavaフレームワークへの汎用対応
* すべてのJavaメソッドを対象とした完全なCall Graph
* 本番環境の常時監視

ただし、他アーキテクチャ向け実装が本リポジトリをフォークしやすいよう、責務とモジュール境界を明確に分離する。

---

## 5. 必須成果物

以下の成果物をすべて作成する。

### 5.1 Mandala SbDP本体

外部のSpring Boot・Doma・PostgreSQLプロジェクトへ導入可能な、Mandala生成ライブラリおよびツールチェーンを作成する。

最低限、以下を提供する。

* Mandala Coreモデル
* Documentation Graphモデル
* Spring Boot解析Adapter
* Doma解析Adapter
* PostgreSQL解析Adapter
* OpenTelemetry Trace取込Adapter
* Playwright収集Adapter
* SQL解析機構
* CRUD分類機構
* ER図生成機構
* 静的HTML生成機構
* カスタムHTML統合機構
* CLI
* Spring Boot Starter
* Gradleタスク
* Agent Skills
* Full Refresh
* Incremental Refresh
* 差分検出
* 整合性検証

### 5.2 サンプルアプリケーション

Mandala生成対象として、一般的なCRUDアプリケーションを同梱する。

題材は、プロジェクトとタスクを管理するシンプルなタスク管理アプリケーションとする。

サンプルアプリケーションは次の構成とする。

* Spring Bootバックエンド
* Doma
* PostgreSQL
* Flyway
* シンプルなWebフロントエンド
* Playwrightで操作可能
* OpenTelemetryで実行経路を取得可能

最低限、以下の機能を持つこと。

* ログイン
* ログアウト
* プロジェクト一覧
* プロジェクト詳細
* プロジェクト作成
* プロジェクト更新
* プロジェクト削除
* タスク一覧
* タスク詳細
* タスク作成
* タスク更新
* タスク削除
* タスク状態変更
* 入力値検証
* 権限不足表示
* データ0件表示
* APIエラー表示
* Not Found表示

DB操作として、CREATE、READ、UPDATE、DELETEをすべて実際に発生させる。

最低限、以下のテーブルを持たせる。

* `users`
* `projects`
* `tasks`
* `audit_logs`

必要に応じて以下を追加してよい。

* `roles`
* `user_roles`
* `project_members`
* `task_tags`
* `tags`
* `sessions`

ログイン機能は簡易的でよいが、平文パスワードを保存しないこと。

ローカル専用の初期ユーザーと認証情報をREADMEに明記する。

### 5.3 サンプルアプリケーションから生成したMandala

サンプルアプリケーションを実際に解析し、Mandala生成結果を出力する。

出力先は以下を基本とする。

```text
mandala/generated/sample-app/
```

最低限、以下を生成する。

* Documentation Graph
* E2E・画面ページ
* 画面スクリーンショット
* 画面状態
* UI操作
* クライアントAPI一覧
* Endpointページ
* Javaシンボルページ
* Doma DAOページ
* SQLページ
* CRUD一覧
* CRUDマトリクス
* テーブル定義ページ
* カラム定義
* ER図
* E2E単位の部分ER図
* 実行経路図
* 解析根拠
* 確度
* stale一覧
* conflict一覧
* 前回解析との差分
* カスタムHTMLを統合したページ

この生成結果は、Mandala SbDP本体の統合テスト、回帰テスト、Golden Test、双方向リンク検証に利用する。

### 5.4 Mandala SbDP公式技術ドキュメント

`/site`配下に、**Mandala SbDP本体についての完全に独立した公式技術ドキュメント**を作成する。

`/site/src`は、サンプルアプリケーションを解析して生成したMandalaの格納場所ではない。サンプルの正規生成元は`/mandala/generated/sample-app/site`とする。

以下を厳密に分離する。

```text
/site
  src: Mandala SbDP本体の公式技術ドキュメント
  dist: GitHub Pages用の再生成可能な公開bundle

/mandala/generated/sample-app
  サンプルアプリケーションを解析したMandala
  site: 公開用静的成果物の正規生成元
  graph/traces/snapshots: 統合テスト・回帰テスト・ローカル確認用
```

`/site/src`と`/mandala/generated/sample-app/site`のソース責務は混在させない。`build-site`は両者から`/site/dist`を再生成し、サンプルの公開用静的成果物だけを`/site/dist/sample`へ投影する。

GitHub Pagesでは、LPを`https://<github-domain>/<repository-name>/`と`https://<github-domain>/<repository-name>/en/`、公式文書を`https://<github-domain>/<repository-name>/docs/<document-path>`と`https://<github-domain>/<repository-name>/docs/en/<document-path>`、サンプルを`https://<github-domain>/<repository-name>/sample/<generated-artifact-path>`で公開する。repository名やdomainは固定埋め込みせず、相対URLと`page-map.json`によるStable ID解決を使う。

サンプルコードや模式図を説明のために引用することは許可するが、サンプルMandalaそのものを公式仕様書の代替として使用しない。

---

## 6. 推奨リポジトリ構成

以下を基本構成とする。

必要に応じて調整してよいが、Mandala本体、解析対象アプリケーション、生成結果、公式技術ドキュメントを混在させないこと。

```text
/
├─ GOAL.md
├─ AGENTS.md
├─ README.md
├─ LICENSE
├─ settings.gradle.kts
├─ build.gradle.kts
├─ gradlew
├─ gradlew.bat
├─ gradle/
│  ├─ wrapper/
│  └─ libs.versions.toml
│
├─ platform/                       # 外部projectへ導入する製品本体
│  ├─ README.md
│  ├─ java/
│  │  ├─ mandala-model/
│  │  ├─ mandala-core/
│  │  ├─ mandala-spring/
│  │  ├─ mandala-doma/
│  │  ├─ mandala-postgres/
│  │  ├─ mandala-opentelemetry/
│  │  ├─ mandala-renderer/
│  │  ├─ mandala-cli/
│  │  ├─ mandala-spring-boot-starter/
│  │  └─ mandala-gradle-plugin/
│  ├─ playwright-capture/
│  └─ agent-skills/
│
├─ sample-app/
│  ├─ backend/
│  ├─ frontend/
│  ├─ fixtures/
│  └─ scenarios/
│
├─ mandala/                        # 利用側に置く解析workspace
│  ├─ README.md
│  ├─ config/
│  ├─ custom/
│  ├─ snapshots/
│  ├─ traces/
│  ├─ cache/
│  └─ generated/
│     └─ sample-app/
│        ├─ graph/
│        ├─ pages/
│        ├─ screenshots/
│        ├─ er/
│        ├─ crud/
│        ├─ reports/
│        └─ site/
│
├─ site/
│  ├─ src/
│  ├─ assets/
│  ├─ scripts/
│  ├─ dist/                        # build-siteが生成するPages bundle
│  │  └─ sample/                   # sample siteの公開用投影
│  └─ package.json
│
├─ infra/
│  └─ local/
│     ├─ README.md
│     ├─ compose.yaml
│     ├─ containers/
│     └─ otel/
│
├─ scripts/                        # 利用者・CI向けの操作入口
│
└─ .github/
   └─ workflows/
```

---

## 7. モジュール責務

### 7.1 `mandala-model`

Mandalaで使用する不変のデータモデルを定義する。

特定のSpring、Doma、PostgreSQL APIへ直接依存させない。

主な責務は以下とする。

* Node
* Edge
* Evidence
* Confidence
* ReviewState
* SourceLocation
* StableId
* DocumentationGraph
* SchemaGraph
* RuntimeGraph
* UiGraph
* Diff
* Conflict
* Stale判定情報

### 7.2 `mandala-core`

解析結果の統合、正規化、検証、差分検出を担当する。

* Graph Merge
* Stable ID生成
* 双方向インデックス
* Evidence統合
* Confidence評価
* Conflict検出
* Stale検出
* Full Refresh
* Incremental Refresh
* キャッシュ
* 影響範囲解析

### 7.3 `mandala-spring`

Spring Boot固有の解析を担当する。

* HTTP Mapping
* Controller
* Handler Method
* Request型
* Response型
* Validation
* Error Response
* Spring MVC
* Spring WebFlux
* Actuator Mapping
* OpenAPI Operation
* BeanまたはApplication Service境界

### 7.4 `mandala-doma`

Doma固有の解析を担当する。

* DAO Interface
* DAO Method
* 外部SQLファイル
* SQLテンプレート
* 動的条件
* Batch
* Procedure
* SQLファイルとJavaシンボルの関連付け

### 7.5 `mandala-postgres`

PostgreSQL実スキーマの解析を担当する。

* Schema
* Table
* Column
* PK
* FK
* Unique
* Check Constraint
* Index
* Sequence
* View
* Materialized View
* Enum
* Domain
* Trigger
* Function
* RLS Policy
* Table Comment
* Column Comment

### 7.6 `mandala-opentelemetry`

OpenTelemetry Traceの収集・取込・正規化を担当する。

* HTTP Server Span
* Application Service Span
* DAO Span
* JDBCまたはR2DBC Span
* 外部HTTP Client
* 非同期処理
* メッセージ処理
* Trace Context
* SQL Span
* Mandala独自属性

### 7.7 `mandala-renderer`

Documentation Graphから静的ドキュメントを生成する。

* E2Eページ
* Endpointページ
* Javaシンボルページ
* DAOページ
* SQLページ
* テーブルページ
* ER図
* CRUDマトリクス
* 実行経路図
* 差分ページ
* カスタムHTML統合
* 双方向リンク
* 静的アセット
* 検索インデックス
* 日本語・英語の説明切替
* System・Light・Darkテーマ

言語とテーマの選択はヘッダー領域に配置する。翻訳対象はRenderer自身が生成するnavigation、見出し、説明、空状態などのUI文言に限定する。解析元から取得した表示名、説明、Javadoc、用語、引用、code、SQL、Stable ID、Evidenceは原文を維持し、外部翻訳serviceへ送信しない。

### 7.8 `mandala-cli`

ローカルおよびCIからMandalaを操作するCLIを提供する。

### 7.9 `mandala-spring-boot-starter`

外部Spring BootアプリケーションへMandala用計装と設定を簡単に導入するStarterを提供する。

---

## 8. Documentation Graph

### 8.1 ノード

最低限、以下のノード種別を扱う。

* `E2E_FLOW`
* `UI_ENTRY`
* `SCREEN`
* `SCREEN_STATE`
* `UI_ACTION`
* `SCREENSHOT`
* `HTTP_CLIENT_CALL`
* `HTTP_ENDPOINT`
* `OPENAPI_OPERATION`
* `REQUEST_SCHEMA`
* `RESPONSE_SCHEMA`
* `JAVA_CLASS`
* `JAVA_METHOD`
* `CONTROLLER`
* `APPLICATION_SERVICE`
* `DOMA_DAO`
* `DOMA_DAO_METHOD`
* `SQL_STATEMENT`
* `TRACE`
* `SPAN`
* `DB_SCHEMA`
* `DB_TABLE`
* `DB_COLUMN`
* `DB_VIEW`
* `DB_MATERIALIZED_VIEW`
* `DB_FUNCTION`
* `DB_TRIGGER`
* `DB_POLICY`
* `CUSTOM_HTML_SECTION`

### 8.2 関係

最低限、以下の関係種別を扱う。

* `HAS_STATE`
* `HAS_ACTION`
* `NAVIGATES_TO`
* `CAPTURED_AS`
* `CALLS_HTTP`
* `MATCHES_OPERATION`
* `ROUTES_TO`
* `ACCEPTS`
* `RETURNS`
* `CALLS`
* `CALLED_BY`
* `EXECUTES`
* `EXECUTES_SQL`
* `READS`
* `CREATES`
* `UPDATES`
* `DELETES`
* `REFERENCES`
* `FK_TO`
* `FIRES_TRIGGER`
* `CALLS_FUNCTION`
* `OBSERVED_IN`
* `DECLARED_BY`
* `INFERRED_FROM`
* `DOCUMENTED_BY`
* `IMPACTS`
* `USED_BY`

逆方向リンク用に同じ関係を重複保存しない。

単一のEdgeを順方向および逆方向に解釈できる逆引きインデックスを構築する。

### 8.3 安定ID

NodeとEdgeには、解析を再実行しても不要に変化しない安定IDを付与する。

例：

```text
screen:/projects/new
endpoint:POST:/api/projects
java:com.example.project.ProjectController#create
dao:com.example.project.ProjectDao#insert
sql:com/example/project/ProjectDao/insert.sql
table:public.projects
column:public.projects.name
flow:project.create.success
```

行番号や一時的なTrace IDだけを主IDに使用しない。

### 8.4 共通メタデータ

各NodeとEdgeには最低限、以下を保持する。

* Stable ID
* 種別
* 表示名
* 説明
* 情報源
* 根拠
* Source Location
* 対象Gitコミット
* 解析日時
* Adapter名
* Confidence
* Review State
* stale状態
* conflict状態
* 警告
* 関連Trace
* 関連Scenario

---

## 9. Evidence、Confidence、Review

### 9.1 Evidence

最低限、以下を区別する。

* `RUNTIME_OBSERVATION`
* `SPRING_MAPPING`
* `OPENAPI`
* `SOURCE_CODE`
* `JAVADOC`
* `DOMA_MAPPING`
* `SQL_STATIC_ANALYSIS`
* `DATABASE_INTROSPECTION`
* `PLAYWRIGHT_OBSERVATION`
* `AGENT_INFERENCE`
* `HUMAN_INPUT`

### 9.2 Confidence

最低限、以下を区別する。

* `OBSERVED`

  * 実行時に観測された事実
* `DECLARED`

  * Spring Mapping、OpenAPI、DBスキーマなどに宣言されている事実
* `INFERRED`

  * 静的解析またはAgentによる推定
* `HUMAN_REVIEWED`

  * 人間が確認した内容
* `CONFLICTED`

  * 情報源間で矛盾している内容
* `STALE`

  * 元となる実装変更後に再確認されていない内容
* `UNKNOWN`

  * 十分な根拠がない内容

不確実な内容を、確定情報と同じ見た目で表示しないこと。

### 9.3 事実と意図

技術的な事実と、業務・設計上の意図を分離する。

技術的事実は、原則として次の優先順位で扱う。

```text
実行時観測
  >
フレームワークが解決した情報
  >
DB実スキーマ
  >
OpenAPI・型定義
  >
静的解析
  >
Agent推論
```

業務目的や設計意図は、原則として次の優先順位で扱う。

```text
人間がレビューした説明
  >
既存の承認済み説明
  >
Javadoc・コメント
  >
Agent推論
```

---

## 10. UI・Playwright解析

Playwrightを通常のE2Eテストだけではなく、UI仕様とクライアント境界を収集するドキュメント実行エンジンとして使用する。

### 10.1 DBへ接続しないUI収集

画面キャプチャでは、原則としてバックエンドやDBへ接続しない。

PlaywrightのRequest Interception、Fixture、HARなどを使用してAPIをモックする。

UI収集とバックエンドRuntime Captureは別工程として実行し、HTTP Methodと正規化Pathで後から統合する。

```text
UI Capture
  Playwright + API Mock
      ↓
画面・操作・クライアントAPI

Runtime Capture
  API Scenario + OpenTelemetry
      ↓
Endpoint・Service・DAO・SQL・DB

HTTP Method + Normalized Path
      ↓
両者を統合
```

### 10.2 画面状態

Agentがフロントエンドコード、条件分岐、API Client、既存Fixtureを解析し、ドキュメント化すべき画面状態を推定できること。

最低限、以下の状態をサンプルで扱う。

* 通常
* Loading
* 0件
* 入力値エラー
* 権限不足
* APIエラー
* Not Found

人間が事前にE2E仕様を書くことを必須にしない。

Agentは以下を行う。

1. Routeを発見する。
2. 画面コンポーネントを特定する。
3. 操作可能なUIを検出する。
4. API Client候補を検出する。
5. 必要なAPIモックを生成する。
6. Playwrightシナリオを生成する。
7. 実行結果を解析する。
8. 暫定E2Eフローを作成する。
9. 人間またはAgentによるレビュー対象を提示する。

### 10.3 安定したスクリーンショット

以下を固定する。

* viewport
* locale
* timezone
* color scheme
* device scale factor
* 現在時刻
* 認証状態
* ユーザー権限
* Feature Flag
* APIレスポンス
* 初期表示データ
* ランダム値
* アニメーション
* Transition
* フォント
* Caret

固定時間待機を主要な同期方法として使用しない。

以下を使用する。

* Playwright Locator
* 期待する画面要素
* Loading終了
* `data-doc-ready`
* ARIA状態
* 明示的な画面安定条件

### 10.4 収集情報

Playwrightから最低限、以下を取得する。

* Route
* Page URL
* 画面状態
* UI操作
* Locator
* Request
* Response
* HTTP Method
* 正規化Path
* Request Body
* Response Status
* 使用したMock
* 画面遷移
* スクリーンショット
* ARIA SnapshotまたはDOM Snapshot
* Console Error
* 未定義通信

Authorization、Cookie、Token、個人情報などは保存しない。

---

## 11. Spring Boot解析

最低限、以下を取得する。

* HTTP Method
* Path
* Controller
* Handler Method
* Class-level Mapping
* Method-level Mapping
* Consumes
* Produces
* Request Body
* Path Parameter
* Query Parameter
* Request Header
* Response型
* Validation
* HTTP Status
* Error Response
* OpenAPI Operation
* Javadocコメント

以下の情報源を適切に組み合わせる。

* Spring Boot Actuator Mappings
* OpenAPI
* Java Source
* コンパイル済みクラス
* Annotation
* Doclet API
* OpenTelemetry

Javadocコメントの先頭説明を、Javaシンボルの自動生成機能概要として同期する。

人間またはAgentが追加した説明がある場合は、Javadoc概要と分離して表示する。

---

## 12. アプリケーション実行経路

OpenTelemetryを使用し、HTTPエンドポイントからDBまでの実行経路を取得する。

最低限、以下の境界を識別する。

* HTTP Server
* Controller
* Application Service
* Use Case
* Doma DAO
* JDBCまたはR2DBC
* 外部HTTP Client
* 非同期Consumer
* メッセージ処理

すべてのprivateメソッドをSpan化しない。

人間がアプリケーションの処理を理解するために重要な境界をSpan化する。

Mandala用の独自属性を定義してよい。

例：

```text
mandala.flow.id
mandala.symbol.id
mandala.layer
mandala.endpoint.id
mandala.dao.id
mandala.sql.id
```

自動計装だけでApplication ServiceやDAO境界を取得できない場合は、Spring Boot Starter、Annotation、Interceptor、AOPなどで補完する。

---

## 13. Doma・SQL解析

### 13.1 DAOとSQLの関連付け

最低限、以下を関連付ける。

```text
DAO Interface
  ↓
DAO Method
  ↓
外部SQLファイル
  ↓
SQL Statement
```

以下を扱う。

* Select
* Insert
* Update
* Delete
* Batch
* Script
* Procedure
* Doma SQL Template
* 動的条件
* 埋め込み変数

### 13.2 SQL解析

SQLを正規表現だけで解析しない。

PostgreSQL方言へ対応した構文解析を使用する。

最低限、以下を抽出する。

* SQL種別
* Schema
* Table
* Column
* JOIN
* WHERE
* Subquery
* CTE
* INSERT対象列
* UPDATE対象列
* DELETE対象
* RETURNING
* Function呼び出し
* View参照

動的SQLで静的に確定できない情報は、OpenTelemetryで観測したSQLと統合する。

---

## 14. PostgreSQL実スキーマ解析

DB構造をJava EntityやDAOから推測するだけでなく、Flywayを適用したPostgreSQL実体から取得する。

本番DBへの接続は前提にしない。

ローカルおよびCIでは一時PostgreSQLまたはDocker Compose上のPostgreSQLを使用する。

### 14.1 tbls連携

tblsまたはtblsと同等の実スキーマ解析方式を利用する。

tblsを利用する場合は、単に生成されたMarkdownを取り込むだけではなく、MandalaのDocumentation Graphへ変換可能な構造化データを取得・保持する。

### 14.2 取得対象

最低限、以下を取得する。

* Database
* Schema
* Table
* Column
* Data Type
* Nullable
* Default
* Primary Key
* Foreign Key
* Unique Constraint
* Check Constraint
* Index
* Sequence
* View
* Materialized View
* Enum
* Domain
* Trigger
* Function
* RLS Policy
* Table Comment
* Column Comment

必要に応じて以下を組み合わせる。

* tbls
* JDBC `DatabaseMetaData`
* `information_schema`
* `pg_catalog`

---

## 15. CRUD解析

HTTPのMethodからDB CRUDを推定しない。

例：

* `POST /search`はDB上ではREADになり得る。
* `DELETE /projects/{id}`でも論理削除ならDB上ではUPDATEになる。
* `POST /projects`が監査ログをCREATEする可能性がある。

CRUDはSQLと実行時観測から判定する。

### 15.1 基本分類

```text
SELECT   → READ
INSERT   → CREATE
UPDATE   → UPDATE
DELETE   → DELETE
MERGE    → CREATE / UPDATE
TRUNCATE → DELETE
```

### 15.2 分類情報

各CRUDには最低限、以下を保持する。

* E2Eフロー
* HTTP Endpoint
* Application Service
* DAO
* DAO Method
* SQL
* Schema
* Table
* Column
* CRUD種別
* 直接操作または間接操作
* ObservedまたはInferred
* 根拠
* Trace
* Scenario
* Confidence

### 15.3 Trigger・Function

以下を区別する。

* アプリケーションから直接実行された操作
* Triggerによる間接操作
* FunctionまたはProcedure内部の操作
* 非同期Consumerによる後続操作

完全解析できない場合は、部分的な根拠と不確実性を明示する。

---

## 16. ER図・テーブル定義・CRUDマトリクス

### 16.1 全体ER図

PostgreSQL実スキーマから、Schema単位またはDB全体のER図を生成する。

以下を満たす。

* Table名を表示
* PKとFKを表示
* Relationを関係カラム同士の線で接続し、両端にCardinalityを表示
* IDEF1X記法とIE（Crow's Foot）記法をページ内で切り替え
* 子TableのPKを構成するFKを識別関係、それ以外を非識別関係として表示
* ERカードは関係把握に必要なPK、FK、Unique、参照先カラムだけを初期表示
* 全カラム表示はTable個別ページへ分離し、ERカードから遷移
* Table検索
* Tableページへのリンク
* 大規模化を考慮した表示切替

### 16.2 E2E単位の部分ER図

各E2Eフローについて、関係するテーブルを中心とした部分ER図を生成する。

各テーブルに、そのE2EでのCRUDを表示する。

例：

```text
users       READ
projects    CREATE
audit_logs  CREATE
```

必要に応じて、直接操作されない隣接テーブルも関係理解のために表示できるようにする。

### 16.3 テーブル定義

各テーブルページに最低限、以下を表示する。

* Schema
* Table名
* Comment
* Column
* Type
* Nullable
* Default
* PK
* FK
* Unique
* Check Constraint
* Index
* Trigger
* Function
* RLS
* 参照先
* 参照元
* 関連SQL
* 関連DAO
* 関連Application Service

### 16.4 CRUD逆引き

テーブルページから、そのテーブルを操作するE2Eへ遷移できること。

```text
CREATE
  - プロジェクト作成

READ
  - プロジェクト一覧
  - プロジェクト詳細

UPDATE
  - プロジェクト更新

DELETE
  - プロジェクト削除
```

E2Eページからテーブル定義へも遷移できること。

### 16.5 CRUDマトリクス

テーブルとE2Eフローを交差させたCRUDマトリクスを生成する。

セルから以下へ遷移可能にする。

* E2Eページ
* Endpoint
* SQL
* Table
* Column
* Trace

---

## 17. 生成するサンプルMandalaページ

サンプルアプリケーションのMandalaには、最低限、以下のページを生成する。

### 17.1 ホーム

* 対象プロジェクト
* 対象コミット
* 最新解析日時
* E2Eフロー数
* 画面数
* Endpoint数
* Javaシンボル数
* SQL数
* Table数
* 未解決警告数
* stale数
* conflict数
* 前回からの主要変更

### 17.2 E2E・画面ページ

* タイトル
* Route
* エントリ画面
* スクリーンショット
* 画面状態
* UI操作
* 画面遷移
* クライアントAPI
* Request
* Response
* Endpoint
* 実行経路
* CRUD
* 関連Table
* 部分ER図
* 関連Javaシンボル
* 関連SQL
* Evidence
* Confidence
* カスタムHTMLセクション

### 17.3 Endpointページ

* HTTP Method
* Path
* Controller
* Handler
* Request
* Response
* Validation
* Error Response
* 利用画面
* E2Eフロー
* 実行経路
* CRUD
* 関連SQL
* 関連Table

### 17.4 Javaシンボルページ

* ClassまたはMethod
* Javadocから同期した機能概要
* 引数
* 戻り値
* 例外
* Annotation
* 呼び出し元
* 呼び出し先
* 関連Endpoint
* 関連DAO
* 関連SQL
* 関連E2E

### 17.5 DAOページ

* DAO Interface
* DAO Method
* 対応SQL
* 呼び出し元
* CRUD
* 関連Table
* 関連E2E
* Runtime Observation

### 17.6 SQLページ

* SQL
* 正規化SQL
* DAO
* DAO Method
* CRUD
* 対象Table
* 対象Column
* JOIN
* 呼び出し元
* 実行されたE2E
* 静的解析結果
* Runtime Observation
* 両者の差異

### 17.7 Tableページ

* テーブル定義
* ER図
* CRUD逆引き
* 関連E2E
* 関連Endpoint
* 関連Service
* 関連DAO
* 関連SQL
* Column逆引き

### 17.8 差分ページ

* 追加された画面
* 削除された画面
* 追加されたEndpoint
* 削除されたEndpoint
* Request変更
* Response変更
* SQL変更
* CRUD変更
* DBスキーマ変更
* 影響するE2E
* staleになった説明
* conflict
* レビューが必要な項目

---

## 18. カスタムHTMLセクション

各ページ全体を自由編集する方式にはしない。

入出力仕様、依存関係、CRUD、ER図、テーブル定義などの自動生成セクションとは独立して、Agentまたは人間が自由なHTMLを配置できるカスタムセクションを提供する。

### 18.1 配置例

```text
mandala/custom/
  entries/
    project-create/
      overview.html
      details.html
      appendix.html
      custom.css

  endpoints/
    post-api-projects/
      details.html

  symbols/
    project-service-create/
      appendix.html

  tables/
    public-projects/
      details.html
```

### 18.2 用途

* 業務背景
* UI設計意図
* 設計判断
* 注意事項
* 運用手順
* FAQ
* 独自図
* 動画
* サンプルコード
* 補足説明
* Agentによる解説
* 人間によるレビュー結果

### 18.3 再生成

再生成によってカスタムHTMLを上書き、削除、初期化しない。

カスタムHTMLと解析結果が矛盾する場合は、自動修正せずconflictとして提示する。

### 18.4 生成項目の参照

カスタムHTMLから安定IDを使って生成項目を参照できるようにする。

例：

```html
<mandala-endpoint-ref
  id="endpoint:POST:/api/projects">
</mandala-endpoint-ref>

<mandala-table-ref
  id="table:public.projects">
</mandala-table-ref>

<mandala-symbol-ref
  id="java:com.example.ProjectService#create">
</mandala-symbol-ref>
```

カスタムCSSはカスタムセクション内へスコープする。

任意JavaScriptの実行は、明示的に有効化しない限り許可しない。

---

## 19. Agent Skills

`platform/agent-skills`配下へ、Mandalaを継続的に生成・更新・検証するためのSkillを作成する。

最低限、以下を作成する。

```text
platform/agent-skills/
  mandala-discover/
  mandala-capture-ui/
  mandala-capture-runtime/
  mandala-analyze-db/
  mandala-reconcile/
  mandala-refresh/
  mandala-review/
```

各Skillに以下を記載する。

* 目的
* 入力
* 前提条件
* 処理手順
* 出力
* Evidence
* 失敗時の扱い
* 禁止事項
* Agentが編集可能な領域
* Agentが自動上書きしてはいけない領域

### 19.1 `mandala-discover`

以下を発見する。

* Frontend Route
* Screen
* UI Action
* API Client
* Spring Endpoint
* Controller
* Application Service候補
* Doma DAO
* SQL
* DB Object
* 既存のカスタムHTML

### 19.2 `mandala-capture-ui`

以下を行う。

* 画面状態の推定
* API Mock生成
* Playwright Scenario生成
* Playwright実行
* Screenshot取得
* API通信取得
* ARIAまたはDOM Snapshot取得
* Console Error取得
* 未定義通信の検出

### 19.3 `mandala-capture-runtime`

以下を行う。

* サンプルバックエンド起動
* API Scenario実行
* OpenTelemetry Trace取得
* HTTP Server Span抽出
* Application Service Span抽出
* DAO Span抽出
* DB Span抽出
* 非同期処理の関連付け

### 19.4 `mandala-analyze-db`

以下を行う。

* PostgreSQL実スキーマ取得
* tbls連携
* SQL解析
* CRUD分類
* Table・Column関連付け
* ER関係生成
* Trigger・Function・RLS取得

### 19.5 `mandala-reconcile`

以下を比較する。

* Source Code
* Javadoc
* Spring Mapping
* OpenAPI
* Playwright Observation
* OpenTelemetry Trace
* Doma Mapping
* SQL
* PostgreSQL Schema
* Custom HTML
* 前回解析結果

以下を検出する。

* conflict
* stale
* 未接続Endpoint
* 未使用Endpoint
* OpenAPIとの差異
* SQLとDB Schemaの差異
* Custom HTMLとの矛盾
* 未解析経路
* Confidence不足

### 19.6 `mandala-refresh`

Mandala全体または影響範囲を再解析し、Documentation Graphと生成サイトを更新する。

### 19.7 `mandala-review`

Agentが生成または推定した以下をレビュー可能な形式へまとめる。

* E2Eフロー
* 画面概要
* 業務目的
* 例外ケース
* 推定されたApplication Service
* 推定されたCRUD
* conflict
* stale
* 差分
* カスタムHTMLの更新候補

---

## 20. 継続更新

解析は一度きりにしない。

### 20.1 Full Refresh

すべての情報源を再取得する。

* Frontend Source
* Playwright
* Spring Mapping
* OpenAPI
* Java Source
* Javadoc
* Doma DAO
* SQL
* OpenTelemetry Trace
* PostgreSQL Schema
* Custom HTML

### 20.2 Incremental Refresh

Git差分やファイル変更から、影響範囲だけを更新する。

最低限、以下を考慮する。

* Java変更
* SQL変更
* Migration変更
* Frontend変更
* Fixture変更
* Playwright Scenario変更
* OpenAPI変更
* Custom HTML変更
* 設定変更

増分更新が安全に実行できない場合はFull Refreshへフォールバックする。

### 20.3 キャッシュ

以下をキャッシュ可能にする。

* DB Schema Snapshot
* OpenAPI
* Spring Mapping
* Trace
* Playwright Observation
* Parsed SQL
* Documentation Graph
* Rendered Asset

キャッシュには対象コミット、設定Hash、Adapter Versionを記録する。

### 20.4 差分

前回のDocumentation Graphと比較して、意味のある差分を生成する。

単なるJSON順序変更や生成時刻変更を仕様差分として扱わない。

---

## 21. CLI

最低限、以下のコマンドを提供する。

```text
mandala init
mandala discover
mandala capture-ui
mandala capture-runtime
mandala analyze-db
mandala reconcile
mandala refresh
mandala render
mandala verify
mandala diff
mandala serve
```

### 21.1 コマンド概要

* `mandala init`

  * 設定ファイル、ディレクトリ、初期テンプレートを作成
* `mandala discover`

  * 対象プロジェクトの境界候補を検出
* `mandala capture-ui`

  * PlaywrightによるUI収集
* `mandala capture-runtime`

  * OpenTelemetry Trace収集
* `mandala analyze-db`

  * PostgreSQL実スキーマとCRUD解析
* `mandala reconcile`

  * 情報源の統合、conflict・stale検出
* `mandala refresh`

  * 一連の解析・生成を実行
* `mandala render`

  * 保存済みGraphからサイトを再生成
* `mandala verify`

  * 整合性、リンク、機密情報、生成差分を検証
* `mandala diff`

  * 前回結果との差分を表示
* `mandala serve`

  * defaultでは設定済み出力先の生成Mandalaを閲覧
  * repository相対rootを指定した場合はPages-ready bundleを閲覧

---

## 22. 設定ファイル

利用者が解析対象と出力を制御できる設定ファイルを用意する。

例：

```yaml
mandala:
  project:
    id: sample-task-app
    name: Mandala Sample Task Application

  source:
    java:
      roots:
        - sample-app/backend/src/main/java
    resources:
      roots:
        - sample-app/backend/src/main/resources
    frontend:
      root: sample-app/frontend

  spring:
    actuatorMappingsUrl: http://localhost:8080/actuator/mappings
    openApiUrl: http://localhost:8080/v3/api-docs

  doma:
    sqlRoots:
      - sample-app/backend/src/main/resources/META-INF

  database:
    type: postgresql
    connection:
      url: jdbc:postgresql://localhost:5432/mandala_sample
      usernameEnv: MANDALA_DB_USERNAME
      passwordEnv: MANDALA_DB_PASSWORD
    schemas:
      - public
    excludeTables:
      - flyway_schema_history

  telemetry:
    traces:
      - mandala/traces/**/*.json

  playwright:
    baseUrl: http://localhost:5173
    scenarios:
      - sample-app/scenarios/**/*.yaml
    output:
      screenshots: mandala/snapshots/screenshots
      observations: mandala/snapshots/ui

  custom:
    root: mandala/custom

  output:
    graph: mandala/generated/sample-app/graph/mandala.json
    site: mandala/generated/sample-app/site

  refresh:
    mode: incremental
    fallbackToFull: true
```

シークレットを設定ファイルへ直接記述しない。

---

## 23. ローカル環境構築・起動スクリプト

新規クローンした利用者が、複雑な手動設定なしで環境を構築できること。

最低限、以下を用意する。

```text
scripts/
  setup.sh
  setup.ps1
  start.sh
  start.ps1
  stop.sh
  stop.ps1
  refresh-mandala.sh
  refresh-mandala.ps1
  serve-mandala.sh
  serve-mandala.ps1
  build-site.sh
  build-site.ps1
  verify.sh
  verify.ps1
```

### 23.1 `setup`

以下を実行する。

* Java確認
* Docker確認
* Node.js確認
* Gradle Wrapper確認
* 依存関係取得
* Playwrightブラウザ取得
* Docker Compose準備
* `.env.example`からローカル設定生成
* 必要ディレクトリ作成

### 23.2 `start`

以下を起動する。

* PostgreSQL
* OpenTelemetry Collector
* 必要なTrace Backend
* サンプルバックエンド
* サンプルフロントエンド

起動完了をHealth Checkで確認する。

### 23.3 `stop`

ローカル環境を安全に停止する。

### 23.4 `refresh-mandala`

以下を実行する。

1. DBスキーマ取得
2. Spring Mapping取得
3. OpenAPI取得
4. Java・Javadoc解析
5. Doma・SQL解析
6. UI Capture
7. Runtime Capture
8. CRUD解析
9. Documentation Graph統合
10. conflict・stale検出
11. サンプルMandala生成
12. リンク検証

### 23.5 `serve-mandala`

Pages-ready bundleをローカルで閲覧可能にする。公式文書はserver root、サンプルアプリケーションから生成したMandalaは`/sample/`で配信し、GitHub Pagesと同じ相対path契約を確認できるようにする。

### 23.6 `build-site`

`/site`配下のMandala SbDP公式技術ドキュメントをビルドする。

`/mandala/generated/sample-app/site`の検証済み静的成果物を`/site/dist/sample`へ投影する。公式文書中のStable ID参照を`page-map.json`が示す実際の成果物名へ解決し、全relative linkを検証する。raw Graph、raw Trace、DB snapshot、local configは含めない。

### 23.7 `verify`

以下を実行する。

* Java Unit Test
* TypeScript Test
* Integration Test
* Playwright Test
* Mandala Full Refresh
* Snapshot Test
* 双方向リンク検証
* 機密情報検査
* サンプルMandalaビルド
* `/site`ビルド
* `/site`リンク検証

標準的な初回手順を、可能な限り以下へ近づける。

```bash
git clone <repository-url>
cd <repository>
./scripts/setup.sh
./scripts/start.sh
./scripts/refresh-mandala.sh
./scripts/serve-mandala.sh
```

Windows PowerShellでも同等の操作を可能にする。

---

## 24. `/site`公式技術ドキュメント

`/site`には、Mandala SbDP本体の技術仕様を説明する独立した公式ドキュメントを作成する。

サンプルアプリケーションの解析結果を読まなくても、Mandala SbDPの目的、構造、導入方法、運用方法、拡張方法を理解できること。

最低限、以下を含める。

### 24.1 概要

* Mandala SbDPとは何か
* 解決する問題
* 対象スタック
* E2E中心のドキュメント
* 相補性のある生きたドキュメント
* 人間とAgentの役割
* 非目標

### 24.2 コンセプト

* Mandala
* Documentation Graph
* Node
* Edge
* Evidence
* Confidence
* Review State
* Conflict
* Stale
* Stable ID
* ObservedとInferredの違い

### 24.3 アーキテクチャ

* システム全体構成
* モジュール構成
* CoreとAdapter
* Capture
* Normalize
* Merge
* Reconcile
* Render
* Full Refresh
* Incremental Refresh

### 24.4 Documentation Graph仕様

* Node型
* Edge型
* Stable ID
* Metadata
* Evidence
* Confidence
* Serialization
* Schema Version
* 後方互換性
* 逆引きインデックス
* Diff形式

### 24.5 Spring Boot解析

* Actuator Mappings
* OpenAPI
* Controller
* Handler
* Request
* Response
* Validation
* Error Response
* MVC
* WebFlux
* Javadoc
* 制約

### 24.6 Doma解析

* DAO
* DAO Method
* SQLファイル
* SQL Template
* 動的条件
* Batch
* Procedure
* Runtime Observationとの統合
* 制約

### 24.7 PostgreSQL解析

* tbls
* JDBC Metadata
* `information_schema`
* `pg_catalog`
* Table
* Column
* Constraint
* Index
* View
* Materialized View
* Enum
* Domain
* Trigger
* Function
* RLS
* ER図

### 24.8 OpenTelemetry統合

* Java Agent
* Spring Boot Starter
* HTTP Server Span
* Application Service Span
* DAO Span
* DB Span
* Trace Context
* 非同期処理
* 独自属性
* SQLの扱い
* 機密情報
* 観測された経路の意味

### 24.9 Playwright統合

* UI Capture
* API Mock
* 画面状態
* Screenshot
* ARIA Snapshot
* Client API収集
* 時刻固定
* viewport固定
* 権限固定
* 安定化
* Runtime Captureとの分離

### 24.10 SQL・CRUD解析

* SQL取得元
* SQL正規化
* PostgreSQL方言
* CRUD分類
* 論理削除
* Trigger
* Function
* Procedure
* テーブル単位
* カラム単位
* ObservedとInferred

### 24.11 生成ドキュメント仕様

* E2Eページ
* Endpointページ
* Javaシンボルページ
* DAOページ
* SQLページ
* Tableページ
* ER図
* CRUDマトリクス
* 双方向リンク
* Custom HTML
* 検索
* Link Validation
* 日本語・英語の説明切替
* System・Light・Darkテーマ
* 解析元の引用・用語・説明を原文維持する翻訳境界

### 24.12 継続更新

* Full Refresh
* Incremental Refresh
* Git差分
* Cache
* Stale
* Conflict
* 影響範囲
* CI
* 失敗時の扱い

### 24.13 Agent Skills

* 各Skillの目的
* 入力
* 出力
* 実行順
* Evidence
* Confidence
* 禁止事項
* 自動編集可能領域
* レビューが必要な領域

### 24.14 導入ガイド

* 必要環境
* Spring Bootへの導入
* Starter
* Doma設定
* PostgreSQL設定
* OpenTelemetry設定
* Playwright設定
* `mandala.yml`
* 初回解析
* 継続解析
* CI
* 出力

### 24.15 CLIリファレンス

すべてのCLIコマンド、引数、終了コード、使用例を記載する。

### 24.16 設定リファレンス

設定項目、必須値、デフォルト値、環境変数、除外設定、マスキング設定、Cache設定を記載する。

### 24.17 拡張ガイド

* Adapter追加
* 新しいNode
* 新しいEdge
* 新しいEvidence
* Renderer追加
* SQL Parser差し替え
* 他DBへの派生
* JPA・MyBatisへの派生
* フォーク時に維持する契約

### 24.18 セキュリティ

* 認証情報
* Cookie
* Token
* SQL Parameter
* Trace
* DB権限
* Custom HTML
* GitHub Pages
* 本番利用時の注意

### 24.19 制約

* 未実行分岐
* 動的SQL
* Reflection
* AOP
* 非同期処理
* Trigger内部
* Procedure内部
* 動的Route
* OpenAPI不足
* Javadoc不足
* 自動生成仕様の限界

### 24.20 コントリビューション

* 開発環境
* モジュール責務
* コーディング規約
* テスト
* Snapshot更新
* Adapter追加
* Pull Request
* Release

---

## 25. GitHub Pages

GitHub Pagesでは`/site/dist`を一つの公開bundleとして配信し、Mandala SbDP公式技術ドキュメントと検証済みサンプルMandalaを公開する。

公開URL契約は以下とする。

* Landing Page: `https://<github-domain>/<repository-name>/`
* English Landing Page: `https://<github-domain>/<repository-name>/en/`
* Docs: `https://<github-domain>/<repository-name>/docs/<document-path>`
* English Docs: `https://<github-domain>/<repository-name>/docs/en/<document-path>`
* Sample Mandala: `https://<github-domain>/<repository-name>/sample/<generated-artifact-path>`

`sample/<generated-artifact-path>`には、`mandala/generated/sample-app/site`が生成したE2E、Screenshot、CRUD Matrix、ER図、Table/Column、Endpoint、DAO、SQL、Evidenceなどの静的公開成果物を、実際の生成名のまま配置する。公式文書からの関連linkはStable IDで管理し、build時に`page-map.json`から実ファイル名へ解決する。

LPはrootと`en/`、公式文書は`docs/`と`docs/en/`へ生成し、旧root直下のDocsは生成しない。公式文書とサンプルMandalaはheaderから説明言語とSystem・Light・Darkテーマを選択可能にする。選択はlocal browser内だけへ保存し、解析元の原文は翻訳しない。

以下をGitHub Pages Artifactへ含めない。

* raw Documentation Graph
* Trace生データ
* DB snapshot
* ローカル用設定
* DB接続情報
* cache、process log、credential

GitHub Pagesがuser siteでもproject siteでも動作するように、repository名やdomainを固定せず相対URLを用いる。

### 25.1 Pull Request Workflow

以下を実行する。

* Java Test
* TypeScript Test
* Playwright Test
* Integration Test
* サンプルMandala生成
* Snapshot検証
* 双方向リンク検証
* 機密情報検証
* `/site/dist`公開bundleビルド
* Docsと`/sample`のlink検証
* Docs内Stable ID成果物linkの解決検証
* コード例・設定例の検証

### 25.2 Main Workflow

以下を実行する。

* `/site/dist`公開bundleビルド
* GitHub Pages Artifact作成
* GitHub Pages配信

Pages Artifactは`site/dist`だけから作成し、その中のLPはrootと`en/`、公式文書は`docs/`と`docs/en/`、サンプルMandalaは`sample/`配下だけへ配置する。raw Graph、raw Trace、DB snapshot、local configはArtifactへ含めない。

実際の外部リポジトリへのデプロイ完了は本作業の必須条件にしない。

Workflow、Artifact生成、ローカルビルドまでを完成させる。

---

## 26. テスト

### 26.1 Unit Test

最低限、以下をテストする。

* Stable ID
* Node
* Edge
* Graph Merge
* 逆引きインデックス
* Evidence統合
* Confidence評価
* Conflict検出
* Stale検出
* Diff
* Spring Mapping変換
* OpenAPI変換
* Doma Mapping
* SQL Parse
* CRUD分類
* PostgreSQL Metadata変換
* Trace変換
* Custom HTML統合
* Link生成

### 26.2 Integration Test

最低限、以下をテストする。

* Spring Boot Endpoint取得
* Doma DAOとSQL関連付け
* Flyway適用後のPostgreSQL解析
* tbls連携
* OpenTelemetry Trace取込
* SQLからTable・Columnへの関連付け
* EndpointからDB CRUDへの関連付け
* ER図生成
* CRUD逆引き
* Custom HTML保持
* Full Refresh
* Incremental Refresh

### 26.3 End-to-End Test

最低限、以下を実行する。

1. サンプル環境起動
2. ログイン
3. CRUD操作
4. UI Capture
5. Client API取得
6. Runtime Trace取得
7. PostgreSQL Schema取得
8. SQL・CRUD解析
9. Documentation Graph生成
10. サンプルMandala生成
11. E2EからTableへ遷移
12. TableからE2Eへ逆遷移
13. ER図からTableへ遷移
14. CRUDマトリクスからE2E・SQLへ遷移
15. Custom HTML保持
16. 差分検出
17. stale検出
18. conflict検出

### 26.4 Golden Test

主要な生成結果をGolden FileまたはSnapshotとして検証する。

意図しない差分を自動承認しない。

Snapshot更新用の明示的なコマンドを用意する。

---

## 27. サンプル解析シナリオ

最低限、以下のシナリオをMandala化する。

* ログイン成功
* ログイン失敗
* プロジェクト一覧
* プロジェクト一覧0件
* プロジェクト詳細
* プロジェクト作成成功
* プロジェクト作成入力エラー
* プロジェクト更新
* プロジェクト削除
* タスク作成
* タスク更新
* タスク状態変更
* タスク削除
* 権限不足
* APIエラー
* Not Found

最初に完成させる縦方向のフローは、**プロジェクト作成成功**とする。

```text
プロジェクト作成画面
  ↓
POST /api/projects
  ↓
ProjectController#create
  ↓
ProjectApplicationService#create
  ↓
ProjectDao#insert
  ↓
INSERT INTO projects
  ↓
CREATE public.projects
  ↓
INSERT INTO audit_logs
  ↓
CREATE public.audit_logs
```

このフローについて、以下をすべて生成する。

* Screenshot
* Screen State
* UI Action
* Request
* Response
* Endpoint
* Java Symbol
* Javadoc概要
* Runtime Trace
* Application Service
* DAO
* SQL
* CRUD
* Table
* Column
* ER図
* 双方向リンク
* Evidence
* Confidence
* Custom HTML

この縦方向のフローを完成させた後、他のシナリオへ展開する。

---

## 28. セキュリティ

以下を生成物へ保存しない。

* Password
* Cookie
* Session ID
* Authorization Header
* Access Token
* Refresh Token
* API Key
* DB Password
* 個人情報
* SQLの機密バインド値
* ローカル環境の秘密情報

SQLは原則としてバインド値を除いた正規化形式で保存する。

Trace、Request、Responseに対するマスキング機構を実装する。

本番DBへの接続を前提にしない。

DB解析ユーザーは読み取り専用を基本とする。

Custom HTMLはリポジトリ内の信頼されたコンテンツとして扱うが、任意JavaScriptをデフォルトで禁止する。

生成物に機密情報が含まれていないか、`mandala verify`とCIで検査する。

---

## 29. README

READMEには最低限、以下を記載する。

* Mandala SbDPとは何か
* 基本思想
* 解決する問題
* 対象スタック
* 非目標
* アーキテクチャ
* モジュール構成
* 新規クローンからの起動方法
* サンプルアプリケーション
* サンプルMandala生成方法
* サンプルMandala閲覧方法
* `/site`の役割
* 公式技術ドキュメントのビルド方法
* 外部プロジェクトへの導入方法
* CLI
* 設定
* Full Refresh
* Incremental Refresh
* Security
* License
* Contribution
* 他アーキテクチャへの派生方針

以下を明確に説明する。

```text
/site
  Mandala SbDP本体の公式技術ドキュメント

/mandala/generated/sample-app
  サンプルアプリケーションのMandala生成結果
```

---

## 30. ライセンス

OSSとして利用、改変、フォークしやすいPermissive Licenseを採用する。

特別な理由がなければApache License 2.0を使用する。

利用する依存関係、CLI、フォント、アイコン、図生成ライブラリのライセンスを確認し、必要なNOTICEまたはライセンス表記を含める。

---

## 31. 完成条件

以下をすべて満たした場合に完成とする。

1. 新規クローンからスクリプトだけで環境構築できる。
2. macOS、Linux、Windows PowerShell向けの実行経路が用意されている。
3. サンプルアプリケーションへログインできる。
4. サンプルアプリケーションでCRUDを実行できる。
5. Spring Boot・Doma・PostgreSQL構成が実際に動作する。
6. PlaywrightのMock環境で安定した画面キャプチャができる。
7. 人間が事前にE2E仕様を書かなくても、Agentが画面とフロー候補を発見できる。
8. Client API一覧を生成できる。
9. Client APIとSpring Endpointを関連付けられる。
10. RequestとResponse仕様を生成できる。
11. Javadocコメントを機能概要として同期できる。
12. OpenTelemetryでEndpointからDBまでの実行経路を取得できる。
13. Controller、Application Service、Doma DAO、SQLを関連付けられる。
14. Flyway適用後のPostgreSQL実スキーマを取得できる。
15. tblsまたは同等方式でDB実態を解析できる。
16. テーブル定義を生成できる。
17. ER図を生成できる。
18. E2E単位の部分ER図を生成できる。
19. SQLとTraceからCRUDを生成できる。
20. 論理削除をUPDATEとして扱える。
21. CRUDからTableページへ遷移できる。
22. Tableページから関連E2Eへ逆遷移できる。
23. Columnから関連SQL・E2Eへ逆遷移できる。
24. CRUDマトリクスを生成できる。
25. CRUDマトリクスから関連ページへ遷移できる。
26. Custom HTMLセクションを配置できる。
27. 再解析してもCustom HTMLが失われない。
28. Custom HTMLと実装の矛盾を検出できる。
29. EvidenceとConfidenceを表示できる。
30. Observed、Declared、Inferred、Human Reviewedを区別できる。
31. conflictを検出できる。
32. staleを検出できる。
33. Full Refreshを実行できる。
34. Incremental Refreshを実行できる。
35. Incremental Refresh不能時にFull Refreshへフォールバックできる。
36. 前回解析との差分を表示できる。
37. サンプルMandalaをローカルで閲覧できる。
38. サンプルMandalaの双方向リンクを自動検証できる。
39. `/site`を独立してビルドできる。
40. `/site`だけでMandala SbDPの技術仕様を理解できる。
41. `/site/src`と`/mandala/generated/sample-app/site`のソース責務が分離され、`build-site`が公開用投影を`/site/dist/sample`へ再生成できる。
42. GitHub Pages ArtifactでLPをrootと`/en/`、Docsを`/docs/<document-path>`と`/docs/en/<document-path>`、サンプルMandalaを`/sample/<generated-artifact-path>`で配信でき、関連Docsから実成果物へStable ID linkで遷移できる。
43. GitHub Pages用Workflowが存在する。
44. READMEだけで初回実行方法を理解できる。
45. 外部Spring Boot・Doma・PostgreSQLプロジェクトへ導入可能な構造になっている。
46. Agent Skillsが実装され、実際の更新フローで使用できる。
47. `scripts/verify.sh`が成功する。
48. `scripts/verify.ps1`が成功する。
49. 主要機能に空実装、疑似コード、未解決TODOを残していない。
50. 生成物ではなく、継続的に再生成できる仕組みが完成している。
51. 公式文書と生成Mandalaのheaderから日本語・英語の説明言語を選択でき、解析元の引用、用語、説明、code、SQL、Stable ID、Evidenceが原文のまま維持される。
52. 公式文書と生成MandalaのheaderからSystem・Light・Darkテーマを選択でき、選択が両成果物で一貫してlocalに永続化される。

---

## 32. 実装順序

以下の順序を基本とする。

### Phase 1：基盤

* Gradleマルチモジュール
* Docker Compose
* PostgreSQL
* OpenTelemetry Collector
* サンプルバックエンド
* サンプルフロントエンド
* ローカルスクリプト

### Phase 2：縦方向の最小フロー

プロジェクト作成成功を対象に、以下を完成させる。

* UI Capture
* Client API
* Spring Endpoint
* Application Service
* Doma DAO
* SQL
* PostgreSQL Table
* CRUD
* Documentation Graph
* 生成ページ
* 双方向リンク

### Phase 3：DBドキュメント

* 実スキーマ解析
* Table定義
* Column定義
* ER図
* 部分ER図
* CRUDマトリクス
* 逆引き

### Phase 4：継続更新

* Stable ID
* Snapshot
* Diff
* conflict
* stale
* Full Refresh
* Incremental Refresh
* Cache

### Phase 5：カスタムHTMLと相補性

* Custom HTML
* Agentによる補足
* 人間による補足
* 矛盾検出
* Review State

### Phase 6：全サンプルフロー

* CRUD全体
* ログイン
* エラー系
* 権限
* 空状態

### Phase 7：公式技術ドキュメント

* `/site`
* CLI Reference
* Configuration Reference
* Extension Guide
* GitHub Pages Workflow

---

## 33. Agentへの実装指示

1. 不要な確認で作業を停止しない。
2. 合理的な判断を行い、判断理由をREADMEまたは技術文書へ記録する。
3. サンプル専用ハードコードをMandala本体へ混入させない。
4. 生成ファイルを直接編集して問題を解決しない。
5. 設定、解析元、Renderer、Custom HTMLを修正して再生成する。
6. 一時的な手作業で完成したように見せない。
7. エラーを握り潰さない。
8. 解析できない内容にはEvidenceと警告を残す。
9. 推測した情報をObservedとして扱わない。
10. Custom HTMLを無断で削除または全面上書きしない。
11. 機密情報を生成物へ含めない。
12. TODO、空のInterface、疑似コードだけで主要機能を終了しない。
13. テストを実行し、実際の生成サイトを確認する。
14. README、AGENTS.md、Skills、設定例、CLI Helpを実装と同期する。
15. `/site/src`とサンプルMandalaの生成元を混在させず、Pages用の公開投影だけを`/site/dist/sample`へ再生成する。
16. 完了時に、実行したコマンド、テスト結果、生成物、既知の制約を報告する。

---

## 34. 最終原則

本プロジェクトの最優先事項は、見栄えだけのデモや、一度だけ生成できるサンプルを作ることではない。

最優先事項は、Spring Boot・Doma・PostgreSQLアプリケーションを対象として、画面からDBまでの実態を継続的に解析し、コード、実行結果、DB構造、Agentの推論、人間の判断を相互に補完しながら更新できるMandalaを生成することである。

**継続的に解析できること、変更へ追従できること、根拠を辿れること、双方向に遷移できること、自由記述を破壊しないことを必達条件とする。**
