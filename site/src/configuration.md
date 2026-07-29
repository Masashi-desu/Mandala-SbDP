---
title: 設定リファレンス
order: 16
description: mandala.ymlの必須値、default、環境変数、除外、mask、cache設定
---
# 設定リファレンス

## ProjectとSource

`mandala.project.id`は必須でStable ID namespaceとoutput metadataに使います。`name`は表示名です。`source.java.roots`、`source.resources.roots`は複数指定可能です。`source.frontend.root`はTypeScript route discoveryの起点です。pathはconfigではなくrepository root相対です。

## SpringとDoma

`spring.actuatorMappingsUrl`、`spring.openApiUrl`はruntime capture先です。offlineでは`mappingSnapshots`と`openApiSnapshots`をglob指定します。`doma.sqlRoots`は`META-INF`を含むresources directoryです。

## Database

```yaml
database:
  type: postgresql
  connection:
    url: jdbc:postgresql://localhost:5432/app
    usernameEnv: MANDALA_DB_USERNAME
    passwordEnv: MANDALA_DB_PASSWORD
  schemas: [public]
  excludeTables: [flyway_schema_history]
  snapshot: mandala/snapshots/db/schema.json
```

secret値ではなく環境変数名を記述します。`schemas` defaultは`public`です。excludeはschema適用後に評価します。

## TelemetryとPlaywright

`telemetry.traces`はOTLP JSON glob、`captureCommand`はargument配列です。Playwrightには解析起点、入力、出力、接続先を明示します。

```yaml
source:
  frontend:
    root: frontend/src
playwright:
  baseUrl: http://127.0.0.1:5173
  scenarios: [scenarios/**/*.yaml]
  observations: mandala/snapshots/ui/**/*.json
  screenshots: mandala/generated/example/screenshots
  webServer:
    command: npm run dev --workspace frontend
    url: http://127.0.0.1:5173/health
    reuseExistingServer: true
    timeoutMs: 120000
  captureCommand: [npm, run, capture:ui]
```

`webServer`は任意です。外部scriptやCI jobがserverを起動する構成では省略でき、設定する場合は`reuseExistingServer: true`で既存serverを安全に再利用できます。`captureCommand`などargument配列を受け付ける項目は、shell一文字列ではなく配列にするとquote injectionを避けられます。

Capture runnerは次の環境変数を受け付けます: `MANDALA_CAPTURE_REPOSITORY_ROOT`、`MANDALA_CAPTURE_CONFIG`、`MANDALA_CAPTURE_FRONTEND_ROOT`、`MANDALA_CAPTURE_SCENARIOS`、`MANDALA_CAPTURE_OBSERVATIONS`、`MANDALA_CAPTURE_SCREENSHOTS`、`MANDALA_CAPTURE_BASE_URL`、`MANDALA_CAPTURE_WEB_SERVER_ENABLED`、`MANDALA_CAPTURE_WEB_SERVER_COMMAND`、`MANDALA_CAPTURE_WEB_SERVER_URL`、`MANDALA_CAPTURE_REUSE_EXISTING_SERVER`、`MANDALA_CAPTURE_WEB_SERVER_TIMEOUT`。対応するCLI optionは`--repository-root`、`--config`、`--frontend-root`、反復可能な`--scenario`、`--observations`、`--screenshots`、`--base-url`、`--web-server-command`、`--web-server-url`、`--web-server-timeout`、`--[no-]reuse-existing-server`、`--no-web-server`です。CLI option、環境変数、`mandala.yml`の順で優先します。

## CustomとOutput

`custom.root`が自由記述の正本です。`allowJavaScript` defaultはfalseです。`output.graph`、`output.site`は必須、`previousGraph`と`diff`はcache/report先です。公式`site`をsample outputに指定しないでください。

生成Mandalaの既定配色は公式LPと同じパレットです。導入先でブランド配色を変える場合は、`custom.root`直下の`palette.css`へLight・Dark双方の公開トークンを指定します。

```css
:root {
  --mandala-light-accent: #3b5ccc;
  --mandala-dark-accent: #9fb3ff;
}
```

対応する接頭辞は`--mandala-light-`と`--mandala-dark-`、主なsuffixは`page`、`paper`、`ink`、`body`、`muted`、`line`、`accent`、`accent-strong`、`gold`、`indigo`、`jade`、`danger`、`header-bg`、`raised`、`diagram-surface`、`diagram-box`、`diagram-stroke`です。`palette.css`は`:root`のこれらの変数宣言だけを許可し、remote importや任意selectorを拒否します。それ以外のCustom CSSは従来どおりCustom HTML内へ自動的にscopeされます。

## RefreshとCache

`refresh.mode`は`incremental` default、`fallbackToFull`はtrue、`baseRef`は`HEAD~1`です。cache manifestにはcommit、config hash、adapter versionを自動保存します。cache directoryはsecretを含まない場合だけCI cacheへ載せます。

## Maskと除外

`security.maskKeys`は大文字小文字を無視して再帰適用します。password、authorization、cookie、token、sessionId、emailがdefaultです。`excludedPaths`はscan/discoveryからbuild output、`.git`、dependency directoryを除きます。
