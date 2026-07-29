# mandala-postgres

Flyway適用後などの実PostgreSQLへJDBC接続し、`information_schema` と `pg_catalog` を読み取るSchema Adapterです。

```java
try (Connection connection = dataSource.getConnection()) {
    PostgresSchemaSnapshot snapshot = new JdbcPostgresSchemaAnalyzer().capture(
            connection,
            new PostgresCaptureOptions(Set.of("public"), Set.of()));
}
```

Schema、Table、partition/foreign table、Column、PK/FK/Unique/Check/Exclusion、Index、Sequence、View、Materialized View、Enum、Domain、Trigger、Function/Procedure、RLS Policy、table/column commentを構造化します。全queryはSELECTのみで、本番接続は前提にしていません。解析用DB userには対象schemaの`USAGE`とcatalog/objectの参照に必要な最小権限だけを与えてください。

実DB統合テストは次の環境変数がある場合に実行されます。テスト専用DBだけを指定してください。テストは一意な一時schemaを作成し、終了時に削除します。

```text
MANDALA_TEST_POSTGRES_URL=jdbc:postgresql://localhost:5432/mandala_test
MANDALA_TEST_POSTGRES_USER=postgres
MANDALA_TEST_POSTGRES_PASSWORD=postgres
```
