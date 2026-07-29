---
title: 制約
order: 19
description: 静的・実行時解析で確定できない内容と、それを誤表示しないための方針
---
# 制約

## 未実行分岐

Runtime Captureは実行したscenarioの経路だけをOBSERVEDにします。未実行branchはsource解析からINFERREDで残り、存在しないとは断定しません。coverageが不足するE2Eをreview reportに出します。

## 動的SQL

Doma templateのruntime条件、embedded variable、application組立SQLは全statementを静的確定できません。template segmentと観測SQLをmergeし、未観測branchへUNKNOWN warningを付けます。

## ReflectionとAOP

Reflection、dynamic proxy、runtime-generated classはsource call graphだけで追跡できません。Spring MappingとTraceで補完します。AOPにより実行順が変わる場合、declaredとobservedを別表示します。

## Async、Trigger、Procedure

trace contextが失われたasync処理は同一flowと確定できません。trigger/function/procedure内部はcatalog定義から部分解析しますが、dynamic executionは不確実です。direct/indirect/asyncを明示します。

## Frontend

runtime生成route、feature flag、browser固有分岐、canvas内容はTypeScript AST/ARIAだけでは完全に発見できません。固定flagとroleごとのcaptureを追加します。Screenshotはpixel上の意図を自動証明しません。

## OpenAPIとJavadoc

不足しても解析を停止しませんが、request/response説明や業務目的のConfidenceは低下します。Agent推論を確定情報の見た目にせず、Custom HTMLやreviewで補います。

## 自動生成の限界

Graphは観測・宣言・推論を整理する道具であり、業務上の正しさを保証しません。Conflictを自動的に多数決で解決せず、人間またはAgentの明示reviewを要求します。
