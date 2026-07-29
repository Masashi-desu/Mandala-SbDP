---
title: 拡張ガイド
order: 17
description: Adapter、Node/Edge、Evidence、Renderer、SQL parser、他DBやframeworkへのfork契約
---
# 拡張ガイド

## Adapter追加

Adapterはframework APIを`mandala-model`へ漏らさず、immutableな解析recordまたはGraph fragmentを返します。input、adapter version、Evidence、source location、warningを必ず持たせ、解析不能をempty successにしません。fixtureとGolden testを追加します。

## NodeとEdge

既存typeで意味が表現できない場合だけenumを追加します。Stable ID grammar、renderer fallback、serialization migration、reverse link、diff、documentationを同時更新します。同じ関係のreverse Edgeを追加しません。

## Evidence

新Evidenceはtechnical factかdesign intentかを定義し、default Confidenceとpriorityを追加します。外部toolの出力を無条件にOBSERVEDへしないでください。source versionと取得時刻を保持します。

## Renderer

Node type固有sectionは共通header、Evidence、relationを維持した上で追加します。output pathはStable IDから決定論的に生成し、all linksをLink Validatorへ通します。Custom HTML sourceをRendererが書き換えてはいけません。

Rendererが追加するUI説明には翻訳keyを付けます。Graph由来のdisplay name、description、ID、SQL、Evidence、引用、解析元の用語は翻訳対象にせず、原文のままescapeして出力します。

## SQL parser差し替え

`SqlAnalyzer` contractはstatement kind、schema/table/column、CTE、JOIN、predicate、RETURNING、function、dynamic segment、warningを返します。PostgreSQL fixture corpusとCRUD classifier testを満たす実装だけを差し替えます。

## 他DB・JPA・MyBatis

PostgreSQL固有catalogはAdapter内に閉じています。別DB forkはschema capability matrixとStable ID contractを維持します。JPA/MyBatisはDoma Adapterへ条件分岐を混ぜず独立moduleとして実装します。

## Forkで維持する契約

canonical JSON、Stable ID、Evidence/Confidence、単一Edge reverse index、Custom HTML非破壊、secret masking、Full fallback、link validationを互換性contractとして維持してください。
