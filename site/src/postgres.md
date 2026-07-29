---
title: PostgreSQL実スキーマ解析
order: 7
description: JDBC metadata、information_schema、pg_catalogから実DBをDocumentation Graphへ変換する方法
---
# PostgreSQL実スキーマ解析

Java EntityからDBを推測せず、Flyway適用済みのPostgreSQLへread-only接続して実態を取得します。JDBC `DatabaseMetaData`と`information_schema`、`pg_catalog`を組み合わせ、tbls相当のstructured schema snapshotを保存します。

## 取得対象

database、schema、table、column type、nullable、default、PK、FK、unique、check、index、sequence、view、materialized view、enum、domain、trigger、function、RLS policy、table/column commentを取得します。除外tableはconfigのschema-qualified matcherで適用します。

取得結果の例: [`public.projects`のテーブル定義](sample-ref:table:public.projects)、[`public.tasks`のテーブル定義](sample-ref:table:public.tasks)、[`public.projects.name`のカラム詳細](sample-ref:column:public.projects.name)。

## テーブル定義

各Tableページは、一般的なテーブル定義書としてSchema、Table名、所有者、RLS、カラム名、型、NULL許可、デフォルト値、コメントを一覧表示します。PK、FK、Unique、Check、Index、参照先は各カラムと詳細欄へ表示し、参照元Table、Trigger、Function、RLS Policy、関連SQL、DAO、Application ServiceもGraph上の実体へリンクします。

テーブル定義の直後には、同じTableを利用する関連E2EをCRUD付きで維持します。DB構造の確認と利用シナリオの逆引きを同じTableページから行えます。

## Snapshot

取得値をStable ID順のJSONへ正規化し、Documentation Graphだけでなく`mandala/snapshots/db`へ構造化snapshotとして保存します。commit、config hash、PostgreSQL server version、adapter versionをmetadataに含めるため、incremental cacheの適用可否を判断できます。

## 権限

本番接続は前提にしません。local/CIの一時PostgreSQLへFlywayを適用し、schema capture userはCONNECT、USAGE、SELECT、catalog参照だけを基本とします。passwordは環境変数から読み、snapshotへ含めません。

## ER関係

PK/FKから`FK_TO` Edgeを作ります。全体ERはschema単位でfilterでき、E2E部分ERは当該flowから到達するtableと任意の隣接tableだけを表示します。ERはTable間のRelationをカラム同士の線で接続し、`0..*`、`0..1`、`1`のcardinalityを表示します。

関係線はIDEF1X記法とIE（Crow's Foot）記法をページ内で切り替えられます。子TableのPKを構成するFKを識別関係、それ以外を非識別関係としてDB定義から判定します。IDEF1Xでは実線／破線、子側ドット、任意の親側ダイヤ、`Z`／`P`を使い、IEでは実線／破線と丸・バー・Crow's Footへ同じ関係性を写像します。この選択はページ内だけで保持し、永続設定には追加しません。

ERカードが初期表示するのは、関係把握に必要なPK、FK、Unique、参照先カラムだけです。全カラムと型、NULL許可、default、comment、constraint、indexの確認は各Tableページへ分離します。ER上のTable名、関係カラム、またはカード下部の導線から該当定義へ移動できます。

[公開サンプルのER図](sample/er/)から各Table pageへ移動できます。

## Trigger、Function、RLS

trigger definitionとfunction identityを別Nodeにし、`FIRES_TRIGGER`、`CALLS_FUNCTION`で接続します。function bodyから推定したCRUDは`indirect=true`かつINFERREDです。RLS policyはcommand、role、using/check expressionを取得しますが、secretに見えるliteralはmaskします。
