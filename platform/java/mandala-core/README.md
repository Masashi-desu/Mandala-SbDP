# mandala-core

Adapterが収集したGraph fragmentを生きたDocumentation Graphへ統合する、framework非依存の処理層です。

## Reconcile pipeline

1. `StableIdGenerator`がHTTP path、Java symbol、DAO、SQL resource、Table、Column、E2E flowから安定IDを生成します。
2. `GraphMerger`が同一IDのfragmentを統合し、全Evidenceを保持します。
3. `ConfidenceEvaluator`が技術的事実と設計意図で別の情報源優先順位を適用します。
4. `ConflictDetector`がtype、review state、構造化属性、Edge endpoint、欠落Nodeの矛盾を構造化します。名称・説明文は相補的な記述として扱い、Custom HTMLの明示assertionは別途照合します。
5. `GraphValidator`がdangling edge、同一関係の重複、保存された逆関係を拒否します。
6. `GraphDiffer`が時刻、commit、JSON順序、行移動を除外した意味差分を生成します。
7. `StaleDetector`がsource fingerprintと影響経路から再レビュー対象を付与します。
8. `ImpactAnalyzer`が変更NodeからE2E flowまでの最短経路と推移的影響を返します。

## Full / Incremental Refresh

外部Adapterは`GraphAdapter`を実装します。増分結果は差分断片ではなく、そのAdapterが所有する完全な置換fragmentを返す契約なので、削除も表現できます。

```java
RefreshEngine engine = new RefreshEngine(adapters, new FileSystemCache(cacheRoot));
RefreshResult result = engine.refresh(RefreshRequest.full(
        "sample-task-app", commit, configurationHash, repositoryRoot));
```

`RefreshPlanner`はJava、SQL、migration、frontend、fixture、Playwright scenario、OpenAPI、Custom HTML、設定変更を分類します。次の場合はFull Refreshへフォールバックします。

- 前回Graphがない
- 設定またはbuild入力が変わった
- 安全に分類できないfileが変わった
- 影響Adapterがincrementalをサポートしない
- 再利用可能な前回Adapter fragmentがない

`fallbackToFull=false`の場合は曖昧な増分更新を実行せず`RefreshException`を返します。人間のEvidenceとCustom HTML Nodeは再収集fragmentと再統合し、実装との矛盾およびstaleを維持します。

## Cache

`FileSystemCache`は次の`CacheKind`を扱います。

- DB schema snapshot
- OpenAPI
- Spring mapping
- Trace
- Playwright observation
- parsed SQL
- Documentation Graph
- rendered asset
- Adapter result

各entryは対象commit、設定hash、Adapter名、Adapter version、作成日時、content SHA-256をmetadataへ記録します。payloadとmetadataは一時fileからatomic moveし、破損または条件不一致のentryはcache missとして扱います。

## Reverse lookup

`BidirectionalGraphIndex`はEdgeを複製せず、outgoing、incoming、predecessor、successor、BFS traversal、最短pathを提供します。TableやColumnからSQL、Endpoint、E2Eへ戻る検索も同じEdge集合から実行します。
