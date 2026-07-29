---
title: Spring Boot analysis
order: 5
description: Spring Adapter integration of Java source, Actuator Mapping, and OpenAPI
---
# Spring Boot analysis

The Spring Adapter imports JavaParser source analysis, Actuator `/actuator/mappings`, and OpenAPI `/v3/api-docs` as separate Evidence and reconciles them by HTTP method and normalized path.

## Captured fields

It composes class- and method-level `@RequestMapping` values and captures method, path, consumes, produces, path/query/header parameters, request body, response type, validation annotations, status, and exception mappings.

See the analyzed [`POST /api/projects`](sample-ref:endpoint:POST:/api/projects) and [`PATCH /api/tasks/{id}/status`](sample-ref:endpoint:PATCH:/api/tasks/%7Bid%7D/status).

## Java source and Javadoc

Controllers, handlers, request/response records, and Application Service candidates are analyzed. The first Javadoc paragraph becomes `JAVADOC` Evidence. Human explanations and Custom HTML remain separate and are not overwritten.

## Framework-resolved values

When annotations and Actuator effective mappings differ, Actuator is preferred as the technical fact while source values remain as a Conflict. OpenAPI operation IDs and schemas are connected as API-contract nodes.

## Error responses

`@ControllerAdvice`, `@ExceptionHandler`, validation, and OpenAPI responses are captured. Declared and observed statuses are displayed together when they differ.

## Constraints

Programmatic routers, runtime-composed paths, and external jars without source cannot always be resolved. Actuator and OpenAPI supplement them; unavailable facts retain warnings and UNKNOWN confidence.
