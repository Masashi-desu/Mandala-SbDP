---
name: mandala-review
description: Prepare and apply evidence-aware reviews of inferred E2E flows, screen and business descriptions, application-service candidates, CRUD, exceptions, semantic diffs, conflicts, stale content, and custom-HTML proposals. Use when generated claims require Agent or human confirmation, rejection, explanation, or follow-up capture.
---

# Mandala Review

## 目的

推論、矛盾、stale、差分をStable ID単位で説明し、review結果をEvidenceとして非破壊に反映する。

## 入力と前提

current/previous Graph、conflict/stale/diff report、source location、Screenshot、Trace、Custom HTMLを入力にする。まず`mandala verify`がstructural errorなしであることを確認する。

## 手順

1. E2E flow、screen概要、業務目的、例外、service候補、CRUDをEvidenceとConfidence順に並べる。
2. Conflictでは両方のsource、観測日時、commit、priorityを提示する。
3. Staleでは旧/current fingerprintと影響E2Eを提示する。
4. accept、reject、needs-capture、needs-human-intentを区別する。
5. review stateと`HUMAN_INPUT`または`AGENT_INFERENCE` Evidenceを更新する。
6. 業務説明が必要ならCustom HTMLへ追記し、生成sectionを編集しない。
7. `mandala reconcile && mandala render && mandala verify`を再実行する。

## 出力とEvidence

review packet、更新review state、resolution、Custom HTML候補、必要なcapture actionを出力する。人間確認は`HUMAN_REVIEWED`、Agent判断はINFERREDのまま区別する。

## 失敗時

根拠にアクセスできない場合はUNKNOWNとneeds-captureを選ぶ。矛盾を都合のよいsourceだけで解消しない。review対象のStable IDが削除済みならprevious graphを参照してmigration候補にする。

## 編集境界と禁止事項

review metadataとCustom HTMLの局所追加は編集してよい。人間の既存文章を全面上書きしない。観測なしにOBSERVEDへ変更しない。generated HTML/JSONを直接編集しない。secretや個人情報を説明へ転記しない。
