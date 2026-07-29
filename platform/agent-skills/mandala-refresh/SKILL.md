---
name: mandala-refresh
description: Run a complete or Git-aware incremental Mandala lifecycle across discovery, UI/runtime capture, PostgreSQL analysis, reconciliation, semantic diff, static rendering, link validation, and secret checks, with safe full-refresh fallback. Use for routine documentation updates, CI regeneration, release preparation, or any repository change that can affect generated Mandala output.
---

# Mandala Refresh

## 目的

解析入力からGraphとstatic siteを継続的に再生成し、再現性と検証結果を残す。

## 入力と前提

repository、config、Git history、必要なlocal servicesを入力にする。作業前にdirty filesを確認し、利用者の変更を保持する。

## 手順

1. migration/config/schema version/adapter version変更ならFullを選ぶ。それ以外はGit diffでIncremental候補を分類する。
2. `mandala refresh --mode incremental`または`--mode full`を実行する。
3. DB schema、Spring/OpenAPI/Java/Javadoc、Doma/SQL、UI、runtime、CRUDを順に取得する。
4. Graphをmerge/reconcileしprevious/current semantic diffとimpactを生成する。
5. Custom HTML sourceを保持したままRendererを実行する。
6. Graph integrity、双方向link、custom ref、secret、snapshot、site分離をverifyする。
7. cache manifestへcommit、config hash、adapter version、fallback reasonを保存する。

## 出力とEvidence

Graph、sample site、Screenshot、ER、CRUD、report、cache manifestを設定先へ出力する。すべて解析元から再生成可能にする。

## 失敗時

Incrementalの安全性を証明できなければFullへfallbackする。`fallbackToFull=false`なら停止する。外部capture failureを古いcacheで成功扱いせず、stale reasonを残す。

## 編集境界と禁止事項

config、source、fixture、scenario、Custom HTML、testを編集してよい。generated outputを手で整えない。snapshot差分を自動承認しない。secretをcommitしない。Pagesへは`build-site`が作る`site/dist/sample`の公開用静的投影だけを含め、raw Graph、raw Trace、DB snapshot、local configを混在させない。
