# Formal Model Sources

This directory is the repository home for checked-in formal model source code.

- `/hyperopen/spec/tla/**` holds TLA+ state-machine models and configs.
- `/hyperopen/spec/lean/**` holds the Lean 4 proof workspace used by `npm run formal:verify` and `npm run formal:sync`.

The wrappers that launch those models remain under `/hyperopen/tools/**`.

- `/hyperopen/tools/tla.clj` runs the TLA+ workflow.
- `/hyperopen/tools/formal.clj` and `/hyperopen/tools/formal/core.clj` run the Lean workflow.

Generated manifests stay under `/hyperopen/tools/formal/generated/**`, transient generated artifacts stay under `/hyperopen/target/**`, and checked-in CLJS vector bridges stay under `/hyperopen/test/hyperopen/formal/**`.
