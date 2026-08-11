# Product

## Register

product

## Users

R0DUMP Manager is used by runtime-analysis testers, ROM maintainers, and R0DUMP integration developers on Android 16 source-built devices. The current productization gate covers the original OnePlus 9 (`lemonade`) target and the crDroid 12 / Redmi 9A (`blossom`) port. Users usually have adb and logcat nearby and iterate through repeated flash, install, dump, scan, and repair cycles.

## Product Purpose

The app configures and operates the system-side R0DUMP runtime from the device: choose a target app or global runtime mode, tune dump strategy and limits, start or stop dump, inspect exported artifacts, and repair dumped dex files. Success means the user can safely set a target, apply sane defaults, run a bounded dump, find output, and recover from missing or malformed artifacts without UI stalls.

## Brand Personality

Technical, calm, precise. The UI should feel like a trusted system utility: compact, explicit, and readable, with risk surfaced where choices can affect stability.

## Anti-references

Avoid hacker-themed decoration, raw-log-first layouts, one long unstructured settings page, duplicate controls for the same setting, ambiguous storage wording, and form controls that do not match the data type or task.

## Design Principles

- Pipeline clarity beats compactness: target, configure, dump, inspect, repair.
- Immediate settings should feel live: remove redundant save affordances when changes are persisted as edited.
- Use one consistent form-control vocabulary across settings.
- Separate safe defaults from experimental and dangerous runtime hooks.
- Every path label must make private working output vs public export understandable.
- Device-side automation must be scriptable through stable `am start` actions, because
  release/debug loops usually run from adb rather than only through touch UI.
- Automation entry points must be tolerant of adb quoting mistakes and avoid persisting
  polluted shell flags into `Settings.Global`.

## Automation Contract

R0DUMP Manager accepts the following intent extras:

```text
r0dump_action=start|stop|scan|repair
r0dump_target_package=<package>
r0dump_output_root=/sdcard/Download/R0DUMP
r0dump_target_process=<optional process>
r0dump_finish=true|false
```

Recommended shell form:

```bash
adb shell 'am start -W --activity-clear-task \
  --es r0dump_action start \
  --es r0dump_target_package com.example.target \
  --es r0dump_output_root /sdcard/Download/R0DUMP \
  --ez r0dump_finish true \
  -n com.android.r0dumpmanager/.MainActivity'
```

Lifecycle:

1. `start` persists config, enables `r0dump.dump.enabled`, force-stops the target, and launches it when a launcher entry exists.
2. `stop` clears `r0dump.dump.enabled`; matching running processes observe the change and
   close the ART runtime with a terminal `stopped` status, while the target App process
   itself is not force-killed.
3. `scan` refreshes the current target output directory.
4. `repair` scans, repairs standard dex outputs, rebuilds raw `dexdata_*.bin`
   with `methods_raw_*.jsonl` header snapshots, and can directly normalize raw
   dexdata that already contains a standard DEX header.
5. `--ez r0dump_raw_mirror true` is a default-off automation-only gate that
   mirrors real ART method/dex dumps into raw dexdata records for product
   validation; it is intentionally absent from the normal UI.

The repository does not ship a generic smoke helper or APK installer. Use the
automation contract above with an explicitly selected local target, then collect
`logcat`, `status.json`, DEX counts, repaired ZIP validation, and final settings
as part of the device-specific test record. This keeps a release checkout from
silently assuming a target package or a different product (for example, the
upstream OnePlus 9 `lemonade` build).

Maintainer docs:

- `docs/r0dump/productization-gates.md`
- `docs/r0dump/hook-matrix.md`
- `docs/r0dump/troubleshooting.md`

## Accessibility & Inclusion

Use Android platform-default semantics and focus order. Maintain readable contrast on light utility surfaces, explicit labels for buttons and switches, touch-first controls, and no required decorative motion.
