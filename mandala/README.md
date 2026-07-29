# Mandala workspace

このディレクトリは製品ソースではなく、Mandalaを導入する側のrepositoryに作られる解析workspaceである。このrepositoryでは`sample-app`への導入例と回帰fixtureを兼ねる。

| Path | 責務 | 管理方針 |
|---|---|---|
| `config/` | 解析対象、Adapter、出力先の宣言 | tracked |
| `custom/` | 人間・Agentが補足する保護領域 | tracked、generatorで上書き禁止 |
| `snapshots/` | 正規化済みUI・Spring・DB観測 | commandから再生成 |
| `traces/` | mask済みruntime evidence | raw秘密を保存しない |
| `generated/` | Documentation Graphと静的Mandala | commandから再生成、手編集禁止 |
| `cache/` | Incremental Refresh用cache | untracked |

生成と検証は`../scripts/refresh-mandala.*`と`../scripts/verify.*`を入口にする。

生成siteの全体配色を導入先向けに変える場合は、`custom/palette.css`へ`:root`の`--mandala-light-*` / `--mandala-dark-*`公開トークンだけを記述する。各entryのCSSはCustom HTML内へscopeされるため、全体テーマの変更には使わない。
