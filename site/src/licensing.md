---
title: ライセンス方針
order: 21
description: Apache-2.0の現状、MIT・ISCとの比較、適用範囲、再ライセンス時の判断材料
---
# ライセンス方針

Mandala SbDPのプロジェクト作成物は現在Apache License 2.0で提供しています。結論として、公開ライブラリ、Starter、CLI、Gradle plugin、Adapterを含む本プロジェクトでは、明示的な特許許諾を備えるApache-2.0の維持を推奨します。

これは一般的な整理であり、個別案件に対する法的助言ではありません。公開や再ライセンスの最終判断では、権利者と配布物の範囲を確認してください。

## 現在のライセンス境界

| 対象 | 適用される条件 |
|---|---|
| `platform/`、`scripts/`、公式文書、sample appなど本プロジェクト独自の作成物 | rootの`LICENSE`にあるApache-2.0 |
| Java／Node.js依存、Gradle、download tool、container image | 各component自身のライセンス。MandalaのApache-2.0へ変更されない |
| LPの*Chakrasamvara Mandala*画像 | Public Domain / CC0-1.0。出典と加工内容を第三者台帳に記録 |
| Mandalaが解析する外部projectのcode、Javadoc、SQL、画面、custom content | 解析元または著作者の条件を維持。Mandalaは生成によって再ライセンスしない |
| 生成されたDocumentation GraphとHTML | Mandala自身のUI・renderer部分と、解析元から抽出された内容の双方を含み得る。配布者が解析元の条件を確認する |

本体ライセンスと依存ライセンスは重ねて適用されるのではなく、原則としてそれぞれの著作物に別々に適用されます。本体をMITまたはISCへ変更しても、Apache-2.0、BSD-2-Clause、EPL-2.0、MIT、CC0などの第三者条件は残ります。

## Apache-2.0、MIT、ISCの比較

| 観点 | Apache-2.0 | MIT | ISC |
|---|---|---|---|
| 利用・改変・商用利用 | 許可 | 許可 | 許可 |
| ソース公開義務 | なし | なし | なし |
| 明示的な特許許諾 | あり。ContributorのContributionに必要な特許claimを対象とし、特許訴訟時の終了条項もある | 条文上は明示なし | 条文上は明示なし |
| 再配布時の主な条件 | License同梱、関連notice保持、変更fileの明示、`NOTICE`がある場合の引継ぎ | copyright noticeとpermission noticeを全copyまたはsubstantial portionへ同梱 | copyright noticeとpermission noticeを全copyへ同梱 |
| Contributionの既定 | Section 5が、明示的に除外されないContributionを同じ条件で受ける | 専用条項なし。projectのContribution policyで補う | 専用条項なし。projectのContribution policyで補う |
| 文量・運用負荷 | 長く、NOTICE管理が必要 | 短く広く認知 | 最短クラスだが、MITより採用例・社内templateが少ない場合がある |
| Mandalaとの相性 | library／plugin／agent toolの企業利用で特許条件を明確にしやすい | 小さなlibraryとして条件を最小化したい場合 | 最小の許諾文を優先し、採用者がISCを受け入れやすい場合 |

MITとISCは非常に近いPermissive Licenseです。ISCはMITの許諾文を短くした性格を持ちますが、免責文の表現は同一ではありません。どちらもApache-2.0のような明示的特許条項、Contribution条項、変更fileの表示義務、NOTICE機構を持ちません。

## それぞれを採用する場合

### Apache-2.0を維持する

現在の`LICENSE`と`NOTICE`を維持し、source releaseへ`LICENSE`、`NOTICE`、第三者台帳を含めます。Apache-2.0 codeを変更して再配布する側には変更fileの明示が必要です。第三者のApache `NOTICE`を含むbinaryをbundleする場合は、その関連noticeも読み取れる形で引き継ぎます。

本プロジェクトでは、複数module、外部project向けAdapter、Gradle plugin、Starter、Agent Skillを公開するため、採用者とContributor双方へ明示的な特許条件を示せる点が実務上の利点です。

### MITへ変更する

本プロジェクト独自部分の`LICENSE`をMIT本文へ交換し、著作権者と年を確定します。README、site footer、package metadata、release archive、source header、Contribution方針にある`Apache-2.0`表記を一括更新します。

再配布条件は簡潔になりますが、明示的特許許諾とApache Section 5のContribution既定を失います。第三者Apache componentをMITへ変更することはできないため、それらのlicense／NOTICEは別途維持します。Apache-2.0由来codeをcopyまたは改変して含めている場合、その部分の条件も残ります。

### ISCへ変更する

MITと同様に、本プロジェクト独自部分の`LICENSE`、copyright、metadataをISCへ更新します。copyright noticeとpermission noticeを全copyへ残す運用を定めます。

条文はMITより短い一方、明示的特許許諾とContribution既定はありません。採用先のlicense allowlistや法務templateでMITほど一般的でない可能性があるため、短さに明確な価値がある場合に選ぶのが妥当です。第三者componentの条件はISCへ変更されません。

## 再ライセンス前の確認

ライセンス変更は文言の置換だけでは完了しません。

1. 既存code・文書の全著作権者を特定し、再ライセンス権限または同意を確認する。
2. 外部からcopy／改変したcodeと生成物を分離し、元ライセンスが残る範囲を確認する。
3. 既存releaseを遡って変更するのか、あるversion以降だけを変更するのかを決める。
4. 単一license変更か、`Apache-2.0 OR MIT`のようなdual licenseかを決める。
5. `LICENSE`、`NOTICE`、README、site、package metadata、artifact、Contribution方針を同時に更新する。
6. binary／container／Pages artifactごとに第三者licenseとnoticeの同梱を検証する。

Apache-2.0のSection 5はContributionをApache-2.0で受ける既定であって、maintainerへ著作権を譲渡する条項ではありません。Contributorが増えた後の単独再ライセンスはできるとは限らないため、変更するなら公開・Contribution受付前の権利関係が明確な段階が扱いやすいです。

## 関連資料

- [第三者componentと画像](third-party.md)
- [Apache License 2.0本文](../legal/LICENSE.txt)
- [Mandala SbDP NOTICE](../legal/NOTICE.txt)
- [第三者台帳](../legal/THIRD_PARTY_NOTICES.txt)
- [Apache Software Foundationによる適用ガイド](https://www.apache.org/legal/apply-license)
- [OSIのMIT License本文](https://opensource.org/license/mit)
- [OSIのISC License本文](https://opensource.org/license/isc)
