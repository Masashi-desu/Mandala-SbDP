# Mandala SbDP platform

このディレクトリは、外部アプリケーションへ導入するMandala SbDP製品本体だけを収容する。

- `java/`: framework非依存モデル、Core、各Adapter、Renderer、CLI、Starter、Gradle Plugin
- `playwright-capture/`: 設定駆動のUI discovery・Mock capture
- `agent-skills/`: discoveryからreviewまでの再利用可能なAgent Skill

サンプル固有コード、生成結果、公式サイト、ローカルDocker構成は置かない。Javaの物理配置は`settings.gradle.kts`で論理Gradle projectへ対応付け、artifact名とtask名は各moduleの責務を表す。
