# Anonymous Function Centralization Baseline (Milestone 0)

Generated at: 2026-02-23 18:29Z

## Reproduction Commands

Run from `/Users//projects/hyperopen`:

    bb tools/anonymous_function_duplication_report.clj --scope src --top-files 25 --top-groups 20 > docs/exec-plans/active/artifacts/2026-02-23-anonymous-function-centralization-src-baseline.txt
    bb tools/anonymous_function_duplication_report.clj --scope test --top-files 25 --top-groups 20 > docs/exec-plans/active/artifacts/2026-02-23-anonymous-function-centralization-test-baseline.txt

## Summary Metrics

`src`:

- scanned_files: `241`
- parse_errors: `0`
- total_lambda_arities: `910`
- duplicate_groups: `59`
- duplicate_occurrences: `142`
- cross_file_duplicate_groups: `28`
- large_duplicate_groups_size_ge_10: `28`

`test`:

- scanned_files: `209`
- parse_errors: `0`
- total_lambda_arities: `2369`
- duplicate_groups: `322`
- duplicate_occurrences: `1239`
- cross_file_duplicate_groups: `139`
- large_duplicate_groups_size_ge_10: `115`

## Artifact Files

- `/hyperopen/docs/exec-plans/active/artifacts/2026-02-23-anonymous-function-centralization-src-baseline.txt`
- `/hyperopen/docs/exec-plans/active/artifacts/2026-02-23-anonymous-function-centralization-test-baseline.txt`

These files contain full top-file and top-duplicate-group listings used by the execution plan.

## Tool Modules

- Entry point: `/hyperopen/tools/anonymous_function_duplication_report.clj`
- CLI option parsing: `/hyperopen/tools/anonymous_function_duplication/cli_options.clj`
- Filesystem/path helpers: `/hyperopen/tools/anonymous_function_duplication/filesystem.clj`
- Anonymous-function analyzer: `/hyperopen/tools/anonymous_function_duplication/analyzer.clj`
- Report rendering: `/hyperopen/tools/anonymous_function_duplication/report_output.clj`
