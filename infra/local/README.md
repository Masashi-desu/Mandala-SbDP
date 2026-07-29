# Local infrastructure

サンプルアプリケーションのローカル検証専用インフラを収容する。

- `compose.yaml`: PostgreSQL、Jaeger、OpenTelemetry Collector
- `containers/`: ローカル検証用container image
- `otel/`: Collector設定

操作入口はrepository rootの`scripts/setup.*`、`scripts/start.*`、`scripts/stop.*`とする。ここへproduction deployment、公開環境のcredential、machine固有値を置かない。
