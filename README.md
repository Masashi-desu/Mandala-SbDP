# Mandala SbDP

Mandala SbDPは、Spring Boot・Doma・PostgreSQLアプリケーションの画面、HTTP API、Javaシンボル、実行Trace、SQL、CRUD、実DBスキーマを一つの **Documentation Graph** に統合し、双方向に辿れる静的技術ドキュメントを継続生成するOSS参照実装です。サンプル設計書を一度出力するツールではなく、実装・宣言・実行時観測・DB実態・人間の補足をEvidence付きで照合する「生きたドキュメント」のツールチェーンです。

```text
E2E / Screen / UI action
          ↓
HTTP client call ↔ Endpoint ↔ Controller ↔ Application Service
                                              ↓
Table / Column ↔ CRUD ↔ SQL ↔ Doma DAO ← Runtime Trace
```

## 解決する問題と基本思想

一般的な設計書は、画面・API・コード・SQL・DB定義が別々に管理され、変更後にリンクと内容が古くなります。Mandalaは、Playwrightの安定したMock観測、Spring/OpenAPI/Actuatorの宣言、Java/Doma/SQLの静的解析、OpenTelemetryの実行結果、PostgreSQL catalogの実態をStable IDで接続します。DBから画面へも画面からDBへも同じEdgeの逆引きで辿れます。

コードや人間の記述を単独の正本とはみなしません。各Node/EdgeにEvidence、Confidence、Git commit、解析日時、Review Stateを保持し、未観測・低確度・stale・conflictを明示します。自動生成領域と `mandala/custom` の自由記述を分離するため、再生成で人間の補足を失いません。

## 対象スタックと非目標

初期版の対象はJava 21、Spring Boot 3.5.3（Spring MVC / WebFlux解析）、Doma 3.9.1、PostgreSQL 16、Flyway、OpenTelemetry、Playwright 1.61.0、Gradle Kotlin DSL、TypeScript、GitHub Actions / Pagesです。依存バージョンは [gradle/libs.versions.toml](gradle/libs.versions.toml)、各 `package.json`、[infra/local/compose.yaml](infra/local/compose.yaml) に固定しています。

JPA/Hibernate、MyBatis、jOOQ、PostgreSQL以外のDB、Node.js/.NET/Pythonバックエンド、全Javaメソッドの完全なCall Graph、本番監視は初期版の非目標です。Adapter境界は独立しており、別アーキテクチャは既存モデルやrendererを再利用して派生できます。

## アーキテクチャとモジュール

収集Adapterは専用の部分Graphを作成し、`mandala-core` が正規化・merge・差分・影響範囲・stale/conflictを計算します。`mandala-renderer` はGraphから静的HTML、CRUD matrix、ER図、検索index、Evidence reportを再生成します。CLIとGradle pluginは同じpipelineを呼び出します。

物理配置は、製品本体を`platform/`、解析対象を`sample-app/`、利用側workspaceを`mandala/`、公式文書を`site/`、ローカル基盤を`infra/local/`へ分離しています。境界の詳細は各directoryのREADMEを参照してください。

| モジュール | 責務 |
|---|---|
| `mandala-model` | framework非依存の不変Node、Edge、Evidence、Diffモデル |
| `mandala-core` | merge、逆引きindex、Confidence、conflict/stale、Full/Incremental Refresh、cache |
| `mandala-spring` | Java source、Spring MVC/WebFlux、OpenAPI、Actuator mapping |
| `mandala-doma` | DAO、外部SQL、PostgreSQL AST、CRUD分類 |
| `mandala-postgres` | read-only JDBCによる `information_schema` / `pg_catalog` 収集 |
| `mandala-opentelemetry` | OTLP JSON取込、Trace正規化、機密値mask |
| `mandala-renderer` | スクリーンショット付き画面接続マップ、SCREEN個別の画面・状態遷移、ER/CRUD/Trace図、検索、Custom HTML統合 |
| `mandala-cli` | `init` から `serve` までのローカル/CI command |
| `mandala-spring-boot-starter` | Application Service / Doma DAO span計装 |
| `mandala-gradle-plugin` | `mandalaDiscover`、`mandalaRefresh`、`mandalaRender`、`mandalaVerify`、`mandalaDiff` |
| `platform/playwright-capture` | API interceptionを用いた決定的なUI状態・通信・screenshot収集 |
| `platform/agent-skills` | discover/capture/analyze/reconcile/refresh/reviewの再利用可能なAgent Skill |
| `sample-app` | 全機能を実際に検証するtask管理アプリ（主成果物ではない） |

## 新規cloneからの起動

前提はGit、Java 21、Node.js 24以上、稼働中のDocker、`curl` です。Unixのruntime smoke testには `jq` も必要です。Gradle本体とDocker Compose本体の事前installは不要で、Gradle Wrapperを使用し、Compose pluginがない場合は固定版standalone binaryを `.tools` へ取得します。

macOS / Linux:

```bash
./scripts/setup.sh
./scripts/start.sh
./scripts/refresh-mandala.sh
./scripts/serve-mandala.sh
```

Windows PowerShell:

```powershell
.\scripts\setup.ps1
.\scripts\start.ps1
.\scripts\refresh-mandala.ps1
.\scripts\serve-mandala.ps1
```

`setup` はJDK/Node/Docker/Wrapperを検査し、固定依存、Playwright Chromium、OpenTelemetry Java agent、Compose imageを取得して `.env.example` から未追跡の `.env` を作ります。`start` は`infra/local/compose.yaml`からPostgreSQL、Collector、Jaegerを起動し、backend、frontendを含む全endpointをHealth Checkします。停止は `./scripts/stop.sh` または `.\scripts\stop.ps1` です。DB volumeは保持されます。ローカルデータも消す場合だけ、停止後に`docker compose --project-directory . --file infra/local/compose.yaml down --volumes`を明示実行します。

起動先:

| 対象 | URL |
|---|---|
| sample frontend | <http://127.0.0.1:5173> |
| backend health | <http://127.0.0.1:18080/actuator/health> |
| OpenAPI | <http://127.0.0.1:18080/v3/api-docs> |
| Jaeger | <http://127.0.0.1:16686> |

### ローカル専用の初期認証

Flyway seedはパスワードをBCrypt cost 12のhashとしてのみ保存します。以下はサンプルのローカル専用アカウントで、本番利用は禁止です。

| 権限 | username | password | 用途 |
|---|---|---|---|
| administrator | `local-admin` | `mandala-admin` | 初期sample project、audit log、Actuator mapping |
| normal user | `local-user` | `mandala-user` | 初期project 0件、CRUD/権限不足状態 |

サンプルはlogin/logout、project/task CRUD、task status変更、validation、権限不足、0件、API error、Not Foundを備え、`users`、`projects`、`tasks`、`audit_logs` に実際のCREATE/READ/UPDATE/DELETEを発生させます。

## サンプルMandalaの生成と閲覧

環境起動後、Full Refreshを実行します。

```bash
./scripts/refresh-mandala.sh --full
# Windows: .\scripts\refresh-mandala.ps1
```

このcommandはPlaywright Mock capture、実API CRUD、OpenAPI/Actuator snapshot、OTLP Trace、PostgreSQL実schema、Java/Doma/SQL、CRUD、reconciliation、HTML render、リンク/機密検証を一続きで実行します。出力は手修正せず、同じcommandで再生成します。

```text
/site
  src: Mandala SbDP本体の独立した公式技術ドキュメント
  dist: GitHub Pagesへuploadする再生成可能な公開bundle

/mandala/generated/sample-app
  サンプルアプリケーションのMandala生成結果
  site: site/dist/sampleへ投影する静的公開元
  graph/traces/snapshots: 統合・回帰・Golden検証用（Pagesには含めない）
```

`./scripts/build-site.sh`はLP、公式文書、検証済みサンプルをそれぞれ分離して`site/dist/`へ生成します。GitHub Pagesの公開契約は次のとおりです。

| 種別 | 公開URL |
|---|---|
| LP（日本語） | `https://<github-domain>/<repository-name>/` |
| LP（English） | `https://<github-domain>/<repository-name>/en/` |
| Docs（日本語） | `https://<github-domain>/<repository-name>/docs/<document-path>` |
| Docs（English） | `https://<github-domain>/<repository-name>/docs/en/<document-path>` |
| Sample Mandala | `https://<github-domain>/<repository-name>/sample/<generated-artifact-path>` |

未公開のURL契約なので、旧root直下のDocsや`en/`直下のDocsは生成しません。repository名を固定埋め込みせず、公式文書から成果物へのStable ID参照を`page-map.json`の実ファイル名へ解決して相対linkにします。`./scripts/serve-mandala.sh`では同じbundleを既定 <http://127.0.0.1:4174/>、Docsを <http://127.0.0.1:4174/docs/overview.html>、サンプルを <http://127.0.0.1:4174/sample/> で閲覧できます。Windowsでは同名の `.ps1` を使用します。

公式Docsと生成Mandalaは、いずれもヘッダーで日本語・英語の説明言語とSystem・Light・Darkテーマを選択できます。選択はbrowser local storageに保存され、両成果物で共有されます。翻訳対象は公式Docsの説明とRendererが所有するUI説明だけです。解析元から取得した表示名、説明、Javadoc、用語、引用、code、SQL、Stable ID、Evidenceは翻訳せず原文を維持します。

## CLI

開発中はGradle Application taskからCLIを実行できます。

```bash
./gradlew :mandala-cli:run --args="--help"
./gradlew :mandala-cli:run --args="refresh --mode FULL"
./gradlew :mandala-cli:run --args="verify"
```

| command | 内容 |
|---|---|
| `init` | 設定、custom/cache directoryの非破壊初期化 |
| `discover` | route、client call、Spring、Java、Doma、SQL境界の発見 |
| `capture-ui` | Playwright収集と観測取込（`--import-only` 可） |
| `capture-runtime` | API scenario実行とTrace取込（`--import-only` 可） |
| `analyze-db` | PostgreSQL catalog解析（`--snapshot-only` 可） |
| `reconcile` | 情報源merge、Confidence、conflict/stale判定 |
| `refresh` | 収集からrender/verifyまで（`--mode FULL|INCREMENTAL`、`--offline`） |
| `render` | 保存済みGraphからCustom HTMLを保持して再描画 |
| `verify` | Graph、リンク、custom参照、機密値を検証 |
| `diff` | timestamp/orderを除くsemantic差分（`--fail-on-change` 可） |
| `serve` | Pages-ready bundleのlocalhost server（sampleは`/sample/`） |

配布形を確認する場合は `./gradlew :mandala-cli:installDist` を実行し、`platform/java/mandala-cli/build/install/mandala/bin/mandala` を使えます。

## 設定と更新方式

標準設定は [mandala/config/mandala.yml](mandala/config/mandala.yml) です。source root、Spring snapshot/URL、Doma SQL root、DB schema、Trace glob、Playwright scenario/output、custom root、Graph/site/diff出力、refresh方針を宣言します。DB passwordは値を書かず `usernameEnv` / `passwordEnv` で環境変数名だけを参照します。ローカル値はGit管理外の `.env` に置きます。

Full Refreshは全Adapterを実行してGraphと静的成果物を再構成します。初回、解析設定変更、schema/migration変更、大規模refactor、cache不整合時に使います。

```bash
./scripts/refresh-mandala.sh --full
```

Incremental RefreshはGit差分をJava/SQL/UI/scenario/config単位に分類し、影響Adapterだけを再実行してcacheと前回Graphを再利用します。境界不明・configuration/schema変更・cache欠落時は設定どおり安全にFullへfallbackし、その理由をreportします。

```bash
./scripts/refresh-mandala.sh --incremental
# Windows: .\scripts\refresh-mandala.ps1 -Incremental
```

人間の補足は `mandala/custom/entries/<entry-id>/` の `overview.html`、`details.html`、`custom.css` へ置きます。生成物は公式LPのLight・Darkパレットを既定値とし、導入先固有の全体配色は `mandala/custom/palette.css` の `--mandala-light-*` / `--mandala-dark-*` 公開トークンで上書きできます。script/event handler/`javascript:` URLはdefaultで禁止され、Stable ID参照とassertionはreconciliation時に検証されます。

## 外部Spring Bootプロジェクトへの導入

このrepositoryを隣接配置するsource-composite導入例です。公開releaseでは同じmoduleを通常のMaven/Gradle座標へ置き換えられます。

1. 利用側 `settings.gradle.kts` にincluded buildとdependency substitutionを追加します。

   ```kotlin
   includeBuild("../mandala-sbdp") {
       dependencySubstitution {
           substitute(module("io.github.mandala.sbdp:mandala-spring-boot-starter"))
               .using(project(":mandala-spring-boot-starter"))
       }
   }
   ```

2. 利用側の `build.gradle.kts` へStarterを追加し、Application Service境界に `@MandalaApplicationService` を付けます。

   ```kotlin
   dependencies {
       implementation("io.github.mandala.sbdp:mandala-spring-boot-starter:0.1.0-SNAPSHOT")
   }
   ```

3. このrepositoryでCLI distributionを作成し、対象repository rootで初期化します。

   ```bash
   ../mandala-sbdp/gradlew -p ../mandala-sbdp :mandala-cli:installDist
   ../mandala-sbdp/platform/java/mandala-cli/build/install/mandala/bin/mandala init
   ```

4. 生成された `mandala/config/mandala.yml` のsource、DB、scenario、outputを対象projectへ合わせます。Spring Boot Actuator `mappings` とOpenAPIを有効化し、OTLP/HTTP JSONをCollectorへexportします。DB解析accountは対象schemaへのread-only権限に限定します。
5. `discover` → `capture-ui` → `capture-runtime` → `analyze-db` → `reconcile` → `render` → `verify`、または `refresh` をCIへ組み込みます。

Gradle plugin IDは `io.github.mandala.sbdp` で、`mandalaDiscover`、`mandalaRefresh`、`mandalaRender`、`mandalaVerify`、`mandalaDiff` を提供します。Adapter追加時はframework固有modelを `mandala-model` に漏らさず、部分GraphとEvidenceを返す境界を維持してください。

## Security

- password、cookie、session ID、Authorization、token、API key、DB password、個人情報、SQL bind値をGraph・Trace・screenshotへ保存しません。
- UI収集はAPI interceptionをdefaultとし、実DBに接続しません。実API/runtime収集は明示的なsample環境だけで行います。
- Trace importerは設定可能なkey-based maskingを再帰適用し、SQLはbind値を除く正規形で保存します。Graph出力はallowlist方式で、`host.name`、`process.*`、`service.instance.id`、HTTP header、ローカルabsolute pathを除外します。
- DB認証情報は環境変数のみ、schema収集はread-only transactionです。`.env`、cache、runtime Trace、process log、download toolはGit管理外です。
- Custom HTMLは信頼されたrepository contentとして扱いますが、任意JavaScriptはdefault拒否し、生成siteはContent Security Policy付きlocalhost serverで確認します。
- `mandala verify` とPR workflowがGraph整合性、リンク、Custom参照、機密patternを検査します。実秘密値をsample値へ置き換えず、漏えい時は履歴から削除してcredentialを失効してください。

## テスト、Contribution、License

全検証は `./scripts/verify.sh` または `.\scripts\verify.ps1` です。Java unit/integration/Golden、TypeScript test/typecheck/build、Playwright capture、実PostgreSQL/API/OTLP Full Refresh、Graph・双方向リンク・機密検査、sample Mandala、Pages-ready bundleとDocs/sample横断link checkを実行します。意図したsnapshot更新は `scripts/update-snapshots.*`、確認のみはUnix `--check` / PowerShell `-Check` を使い、生成物を手編集しないでください。

変更は小さな責務単位で行い、bug fixには再現test、Adapter追加にはEvidence/Stable ID/増分影響判定のtestを追加してください。PRは [.github/workflows/pull-request.yml](.github/workflows/pull-request.yml) と同じ検証を通します。Pages workflowのArtifactは`site/dist`のみに保ち、公式文書と`sample/`の公開用静的投影だけを含めます。raw Graph、raw Trace、DB snapshot、local configは混入させないでください。詳細なrepository規約は [AGENTS.md](AGENTS.md)、公式文書のcontribution guideは [site/src/contributing.md](site/src/contributing.md) を参照してください。

本project独自の作成物は [Apache License 2.0](LICENSE) です。依存library、tool、container、画像はそれぞれのlicenseを維持し、直接利用するcomponent、採用license、version、配布境界を [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) に記録します。法的なattribution noticeは [NOTICE](NOTICE)、Apache-2.0／MIT／ISCの比較と適用範囲は [license方針](site/src/licensing.md) を参照してください。

Mandalaが外部projectから解析・引用するcode、Javadoc、SQL、画面、Custom HTMLをApache-2.0へ変更するものではありません。生成物を公開する側が解析元のlicenseと権利を確認してください。別framework/DBへのforkでは、core/model/rendererを維持し、Spring/Doma/PostgreSQL相当を新Adapterとして差し替える方針を推奨します。
