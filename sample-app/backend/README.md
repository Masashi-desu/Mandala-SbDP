# Sample task-management backend

Spring Boot 3.5.3、Doma、PostgreSQL、Flyway で構成した Mandala SbDP の解析対象です。
認証は Spring Security の HTTP session を使用し、パスワードは BCrypt（cost 12）でのみ保存します。

## ローカル専用アカウント

| role | username | password |
|---|---|---|
| administrator | `local-admin` | `mandala-admin` |
| normal user（初期プロジェクト0件） | `local-user` | `mandala-user` |

これらは Flyway の開発用 seed です。本番環境では使用しないでください。

## 起動

PostgreSQL に database/user/password がすべて `mandala` のDBを作成してから、リポジトリ直下で実行します。

```bash
./gradlew :sample-app:backend:bootRun
```

接続情報は `DATABASE_URL`、`DATABASE_USERNAME`、`DATABASE_PASSWORD` で上書きできます。
OpenTelemetry の OTLP/HTTP 送信先は既定で `http://localhost:4318/v1/traces` です。Collectorを使わない場合は `OTEL_EXPORT_ENABLED=false` を指定します。

起動後の実DB/API smoke testには `curl` と `jq` が必要です。

```bash
BASE_URL=http://localhost:8080 ./sample-app/backend/scripts/smoke-test.sh
```

## API

- `POST /api/auth/login`、`POST /api/auth/logout`、`GET /api/auth/me`
- `/api/projects` と `/api/projects/{id}` の CRUD
- `/api/projects/{projectId}/tasks` の一覧・作成
- `/api/tasks/{id}` の取得・更新・削除、`PATCH /api/tasks/{id}/status`
- 管理者専用 `GET /api/audit-logs`
- OpenAPI: `/v3/api-docs`、Swagger UI: `/swagger-ui.html`
- Actuator: `/actuator/health`、管理者専用 `/actuator/mappings`

一般ユーザーは自分が所有するプロジェクトだけを操作できます。存在しないリソースは `404`、他人のプロジェクトは `403`、未認証は `401` を統一JSONエラーで返します。
