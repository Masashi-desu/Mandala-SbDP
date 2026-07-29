---
title: Licensing policy
order: 21
description: Current Apache-2.0 scope, comparison with MIT and ISC, and relicensing considerations
---
# Licensing policy

Mandala SbDP's project-authored work is currently offered under the Apache License 2.0. For this project, which includes public libraries, a Starter, CLI, Gradle plugin, and adapters, the recommendation is to retain Apache-2.0 because it includes an explicit patent grant.

This page is a general project-maintenance summary, not legal advice for a particular matter. Confirm the relevant copyright holders and the contents of each distribution before release or relicensing.

## Current licensing boundaries

| Subject | Applicable terms |
|---|---|
| Project-authored work in `platform/`, `scripts/`, the official documentation, and the sample app | Apache-2.0 in the root `LICENSE` |
| Java and Node.js dependencies, Gradle, downloaded tools, and container images | Each component's own license; Mandala's Apache-2.0 does not replace it |
| The landing-page *Chakrasamvara Mandala* image | Public Domain / CC0-1.0; provenance and transformations are recorded in the third-party inventory |
| Code, Javadoc, SQL, screens, and custom content from an external project analyzed by Mandala | The source project's or author's terms remain in effect; generation by Mandala does not relicense them |
| Generated Documentation Graph and HTML | May contain both Mandala-owned UI/renderer material and extracted source-project material; the distributor must review the source-project terms |

The project license and dependency licenses generally apply to their respective works rather than replacing one another. Changing Mandala itself to MIT or ISC would not remove third-party Apache-2.0, BSD-2-Clause, EPL-2.0, MIT, or CC0 conditions.

## Apache-2.0, MIT, and ISC

| Topic | Apache-2.0 | MIT | ISC |
|---|---|---|---|
| Use, modification, and commercial use | Permitted | Permitted | Permitted |
| Source-disclosure requirement | None | None | None |
| Express patent grant | Yes, for contributor patent claims necessarily infringed by a Contribution, with a patent-litigation termination provision | Not express in the text | Not express in the text |
| Main redistribution duties | Include the License, retain relevant notices, mark modified files, and carry forward applicable `NOTICE` content | Include the copyright and permission notice in all copies or substantial portions | Include the copyright and permission notice in all copies |
| Default for Contributions | Section 5 accepts Contributions under the same terms unless expressly excluded | No dedicated contribution clause; use project policy | No dedicated contribution clause; use project policy |
| Length and operational cost | Longer; requires NOTICE discipline | Short and widely recognized | Very short, but may be less common than MIT in organizational templates |
| Fit for Mandala | Makes patent terms explicit for enterprise use of libraries, plugins, and agent tooling | Suitable when minimizing conditions for a small library is the priority | Suitable when the shortest permission notice has concrete value and adopters accept ISC |

MIT and ISC are closely related permissive licenses. ISC is a compact permission grant, but its disclaimer is not identical to MIT's. Neither contains Apache-2.0's express patent, Contribution, changed-file, or NOTICE provisions.

## What adoption would require

### Retaining Apache-2.0

Keep the current `LICENSE` and `NOTICE`, and include `LICENSE`, `NOTICE`, and the third-party inventory in source releases. A redistributor modifying Apache-2.0 code must mark changed files. If a binary distribution bundles a third-party Apache work with a `NOTICE`, its applicable notices must remain readable.

Mandala has multiple modules, adapters for external projects, a Gradle plugin, Starter, and Agent Skills. Express patent terms for adopters and Contributors are therefore a practical advantage.

### Switching to MIT

Replace the license for project-authored work with the MIT text and identify the copyright holder and year. Update Apache-2.0 references in the README, site footer, package metadata, release archives, source headers, and contribution policy as one coordinated change.

Redistribution becomes simpler, but the project loses an express patent grant and Apache Section 5's default Contribution terms. Third-party Apache components cannot be relicensed to MIT, so their licenses and notices remain. Apache-derived code copied or modified into this repository also retains its applicable obligations.

### Switching to ISC

As with MIT, replace the project license, copyright statement, and metadata for project-authored work. Establish a release process that retains the copyright and permission notice in all copies.

The text is shorter than MIT, but it has no express patent grant or dedicated Contribution default. ISC may also be less common in an adopter's license allowlist or legal templates. It is most useful when that brevity has a concrete benefit. Third-party components do not become ISC-licensed.

## Before relicensing

A license change is more than replacing a text file.

1. Identify every copyright holder and confirm the right or consent to relicense existing code and documentation.
2. Separate copied or modified external material and determine where its original terms remain.
3. Decide whether the change applies only from a future version or is intended to cover earlier releases.
4. Choose a single new license or a dual-license expression such as `Apache-2.0 OR MIT`.
5. Update `LICENSE`, `NOTICE`, the README, site, package metadata, artifacts, and contribution policy together.
6. Verify third-party licenses and notices for every binary, container, and Pages artifact.

Apache-2.0 Section 5 provides default licensing terms for Contributions; it does not assign a Contributor's copyright to the maintainer. Unilateral relicensing may therefore become impossible after additional Contributors arrive. If a change is desired, it is easiest while ownership is clear and before public Contributions are accepted.

## References

- [Third-party components and media](third-party.md)
- [Apache License 2.0 text](../../legal/LICENSE.txt)
- [Mandala SbDP NOTICE](../../legal/NOTICE.txt)
- [Third-party inventory](../../legal/THIRD_PARTY_NOTICES.txt)
- [Apache Software Foundation application guide](https://www.apache.org/legal/apply-license)
- [OSI MIT License text](https://opensource.org/license/mit)
- [OSI ISC License text](https://opensource.org/license/isc)
