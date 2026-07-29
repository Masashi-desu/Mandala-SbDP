# mandala-model

Framework非依存のDocumentation Graph契約を提供するモジュールです。Spring、Doma、PostgreSQLのAPIへは依存しません。

## 公開モデル

- `Node` / `NodeType`: 画面、Endpoint、Java、DAO、SQL、Trace、DBオブジェクト、Custom HTMLを表現します。
- `Edge` / `EdgeType`: Node間の順方向の関係を一度だけ保存します。逆方向の検索は`mandala-core`の`BidirectionalGraphIndex`が担当します。
- `ElementMetadata`: Evidence、Source Location、対象commit、解析日時、Adapter、Confidence、Review State、stale、conflict、警告、Trace、Scenarioを保持します。
- `Evidence`: 技術的事実と設計意図を`EvidenceScope`で分離し、情報源ごとの根拠を保持します。
- `DocumentationGraph`: schema version、project、commit、解析時刻、Node、Edgeからなる不変のスナップショットです。
- `SchemaGraph` / `RuntimeGraph` / `UiGraph`: Documentation Graphの用途別ビューです。
- `Diff`: 追加、削除、意味変更、影響Nodeを型付きで表現します。

`Node`、`Edge`、`DocumentationGraph`はID順へ正規化されます。属性はJSON互換のscalar、list、mapに限定し、map keyを再帰的に整列した不変値として保持します。

## Stable ID

`StableId`は`namespace:semantic-identity`形式です。行番号、解析時刻、一時的なTrace IDは主IDへ含めません。

```java
Node endpoint = Node.builder(
        StableId.of("endpoint:POST:/api/projects"),
        NodeType.HTTP_ENDPOINT,
        "Create project")
    .attributes(Map.of("method", "POST", "path", "/api/projects"))
    .build();
```

## JSON

`DocumentationGraphJson`がCLI、cache、rendererで共通利用するcanonical codecです。

```java
DocumentationGraphJson.write(output, graph);
DocumentationGraph restored = DocumentationGraphJson.read(output);
```

- schema version: `1.0`
- Stable ID: JSON string
- time: UTCを含むISO-8601 string
- Node / Edge / Evidence / attribute: 安定順序
- 未知field: 誤ったschemaを黙って受理しないようエラー

Graph schemaを変更する場合は`DocumentationGraph.CURRENT_SCHEMA_VERSION`を更新し、旧versionからの明示的なmigrationを読み込み境界へ追加します。
