---
title: Spring Boot解析
order: 5
description: Java source、Actuator Mapping、OpenAPIを統合するSpring Adapter
---
# Spring Boot解析

Spring AdapterはJavaParserによるsource解析、Actuator `/actuator/mappings`、OpenAPI `/v3/api-docs`を別々のEvidenceとして取り込み、HTTP methodと正規化pathでreconcileします。

## 取得項目

class/method level `@RequestMapping`を合成し、method、path、consumes、produces、path/query/header parameter、request body、response type、validation annotation、status、exception mappingを取得します。MVCとWebFluxのmapping annotationを同じcanonical descriptorへ変換します。

解析結果の例として[`POST /api/projects`](sample-ref:endpoint:POST:/api/projects)と[`PATCH /api/tasks/{id}/status`](sample-ref:endpoint:PATCH:/api/tasks/%7Bid%7D/status)を公開しています。

## Java sourceとJavadoc

Controller、handler、request/response record、Application Service候補を解析します。Javadocの先頭paragraphは`JAVADOC` Evidenceの自動概要です。Custom HTMLや人間の説明とはfieldを分けるため、再生成で上書きしません。source locationはrepository-relative pathとlineを保持します。

## Framework解決値

annotationの静的値とActuatorの実効mappingが違う場合、Actuatorを技術的事実として優先し、source値を削除せずConflictへ残します。OpenAPI operation IDとschemaはAPI契約としてEndpointに接続します。

## Error response

`@ControllerAdvice`、`@ExceptionHandler`、validation、OpenAPI responseを収集します。runtimeで観測されたstatusと違う場合はEndpointページにdeclared/observedを並べます。

## 制約

programmatic router、runtimeで組み立てるpath、外部jar内の未添付sourceは完全に解析できません。Actuator/OpenAPIで補完し、取得できない場合はwarningとUNKNOWNを残します。
