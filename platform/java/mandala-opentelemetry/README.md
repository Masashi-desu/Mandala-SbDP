# mandala-opentelemetry

OTLP protobuf-JSON trace export（plain JSON / gzip）を正規化する取込Adapterです。

```java
OtlpTraceBatch batch = new OtlpJsonTraceImporter().importFile(Path.of("traces.json.gz"));
```

Resource、Instrumentation Scope、Trace/Span parent関係、Span Kind/Status、Event、Link、attributeを保持し、HTTP Server、Controller、Application Service、Use Case、Doma DAO、JDBC/R2DBC、外部HTTP Client、非同期・message境界へ分類します。`mandala.layer` などのMandala独自attributeがある場合は宣言された境界を優先します。

秘密情報は取込境界の `SensitiveDataMasker` で除去されます。既定でpassword、Authorization、Cookie、session/token/API key、request/response body、代表的な個人情報、DB接続文字列、exception message/stack traceをredactし、`db.statement` / `db.query.text` のliteralを `?` へ置換します。追加のapplication固有keyは `MaskingConfiguration` で指定できます。取込結果にはraw payloadやSQL bind値を保持しません。
