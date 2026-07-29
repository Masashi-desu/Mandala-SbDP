---
title: Doma・SQL解析
order: 6
description: DAO、外部SQL、template、動的条件、PostgreSQL構文解析の関連付け
---
# Doma・SQL解析

Doma Adapterは`@Dao` interface、`@Select`、`@Insert`、`@Update`、`@Delete`、batch、script、procedure annotationを解析し、method signatureからDoma規約の外部SQL pathへ接続します。

## DAOから外部SQL

```text
dao:com.example.ProjectDao#insert
  EXECUTES_SQL
sql:META-INF/com/example/ProjectDao/insert.sql
```

SQL fileがないannotation modeも区別します。overloadはparameter typeを補助identityに使います。missing SQLは握り潰さずwarningとなります。

公開サンプルでは[`ProjectDao`](sample-ref:dao:io.github.mandala.sbdp.sample.database.dao.ProjectDao)から[`insert(ProjectEntity)`](sample-ref:dao:io.github.mandala.sbdp.sample.database.dao.ProjectDao%23insert%28ProjectEntity%29)と外部SQLを辿れます。

## Template

Domaの`/*%if*/`、`/*%for*/`、bind、literal、embedded variableをtemplate segmentとして保持します。全分岐を確定できない場合、静的に成立するstatement候補を解析し、runtime SQLとmergeします。embedded variableは安全上値を保存しません。

## PostgreSQL parser

SQLはJSqlParserのASTでSELECT、INSERT、UPDATE、DELETE、MERGE、CTE、subquery、JOIN、RETURNING、function、columnを抽出します。正規表現だけをsemantic parserに使いません。template前処理と失敗位置はEvidenceに記録します。

## Runtimeとの統合

static SQLとJDBC Spanのnormalized SQLを比較します。一致すればOBSERVEDへ昇格し、table/column差分があればConflictです。bind値は`?`へ正規化し、parameter値をGraphへ保存しません。

## Batch・Procedure

batchは一つのDAO methodと複数実行を関連付けます。procedure/function内部のCRUDはDB introspectionで定義を取得できる範囲だけINFERREDとして追加し、applicationが直接実行したCRUDと区別します。
