# Contributing

1. Base changes on the revisions recorded in `manifests/base-revisions.tsv` or document the
   new rebase explicitly.
2. Keep one patch per Android Git project and update `patches/series.tsv`.
3. Do not commit proprietary blobs, build outputs, signing keys, device backups, APK/DEX
   samples, local paths, or device identifiers.
4. Run `scripts/verify-release-tree.sh` and `scripts/check-patches.sh <android-tree>`.
5. For runtime changes, report ARM32/ARM64 build status and the exact device-side workflow
   tested. A compile-only result must not be described as device-verified.

