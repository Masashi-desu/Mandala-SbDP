# mandala-doma

Doma DAOと外部SQLを静的解析するAdapterです。

```java
DomaAnalysis result = new DomaSourceAnalyzer().analyze(
        Path.of("src/main/java"),
        Path.of("src/main/resources"));
```

`@Dao` とDomaのSelect/Insert/Update/Delete/Batch/Script/Procedure/Function annotationを解析し、`META-INF/<DAOの完全修飾名>/<method>.sql`（Scriptは `.script` も対象）へ関連付けます。Doma SQL Templateはcomment tokenをlexerで解析し、条件、loop、bind/literal/embedded variableを保持します。条件分岐は代表branchを選び、JSqlParserへ渡せるSQLを生成します。不確定な動的branchはwarningと `dynamicTemplate=true` で明示されます。

`PostgresSqlAnalyzer` はJSqlParserのASTからPostgreSQL SQLのstatement種別、CTE、subquery、table/schema、column、JOIN、WHERE、RETURNING、function、CRUDを抽出します。SQL文字列はliteralを `?` へ置換した正規化形式だけを結果へ保持します。HTTP methodからCRUDを推定しません。
