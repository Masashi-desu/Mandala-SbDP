# Mandala Spring Boot Starter

Spring Boot applicationへ Mandala の OpenTelemetry span 計装を追加する starter です。classpath に置くだけで auto-configuration が有効になります。

## 計装境界

- `@MandalaApplicationService` を付けたclassまたはmethodを Application Service spanとして記録
- Spring beanになった `*Dao` / Doma生成 `*DaoImpl` のpublic methodを Doma DAO spanとして記録
- 例外をspanへ記録し、statusを `ERROR` に設定して元の例外を再throw

各spanには `mandala.layer`、`mandala.stable_id`、`mandala.java.class`、`mandala.java.method` とOpenTelemetryの `code.namespace` / `code.function` が入ります。stable IDは `java:<fqcn>#<method>` または `dao:<fqcn>#<method>` です。

```java
@Service
@MandalaApplicationService
public class ProjectService {
    // public application methods
}
```

```yaml
mandala:
  tracing:
    enabled: true
    instrumentation-name: com.example.my-application
```

アプリケーションが `OpenTelemetry` beanを提供している場合はそれを使用し、ない場合は `GlobalOpenTelemetry` へフォールバックします。HTTP/JDBCなどの汎用spanはOpenTelemetry Java agent等で収集し、このstarterのservice/DAO spanと同じcontextで関連付けます。
