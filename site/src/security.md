---
title: セキュリティ
order: 18
description: 認証情報、Cookie、Token、SQL、Trace、DB権限、Custom HTML、公開用静的成果物の安全設計
---
# セキュリティ

## 保存しない情報

password、Cookie、session ID、Authorization、access/refresh token、API key、DB password、個人情報、SQL bind値をGraph、observation、Trace正規化結果、static HTMLへ保存しません。request/responseとspan attributeは再帰maskします。

## 認証情報

configには`usernameEnv`と`passwordEnv`だけを書きます。`.env`はgitignore対象です。sample local credentialは実環境へ転用せず、password hashはbcryptでDBへseedします。

## SQLとTrace

SQLはliteral/bindを`?`へ正規化します。raw OTLPはlocal/CI artifactとして短期保持し、Pages、release、public build cacheへ含めません。verifyはBearer、private key、cloud access key、password assignmentをscanします。

## DB権限

schema captureはread-only userを使い、migration userを使い回しません。本番DB接続はdefault workflowに含めません。接続先allow list、timeout、SSLは外部project側で設定します。

## Custom HTML

repository内のtrusted contentとして扱いますが、script、inline event handler、`javascript:` URLはdefaultで除去します。任意JavaScript opt-inはreviewを必須にし、配信時はホスティング環境側でContent Security Policyを設定します。

## 公開用静的成果物

公開に使用する静的bundleには、Rendererが生成したHTML、検索index、page map、Screenshotなどの公開用投影だけを含めます。raw Documentation Graph、raw OTLP Trace、DB snapshot、local config、credentialは含めません。

この境界はGitHub Pagesに限らず、任意の静的ホスティング先へ適用します。公開元は生成済みのsite rootへ限定し、repository全体や`mandala` workspaceをdocument rootまたはupload対象にしません。

言語・テーマ選択はブラウザlocal storageへ`ja|en`と`system|light|dark`だけを保存します。外部翻訳serviceやremote catalogへ接続せず、解析元の原文を外部送信しません。

## Threat model

主な脅威は観測dataからのsecret漏洩、untrusted HTML、意図しない外部request、過剰DB権限、生成siteのpath traversalです。captureはundefined APIを遮断し、serverはnormalized root外pathを拒否します。
