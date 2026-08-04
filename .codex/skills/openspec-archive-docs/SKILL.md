---
name: openspec-archive-docs
description: Use when one or more OpenSpec changes have just been archived in the BusIsComing repository and the fixed batch must be reconciled with long-lived project documentation.
---

# Reconcile Documentation After OpenSpec Archive

## Scope

Treat one archived change as a complete batch of one. When several changes are archived in the same user request, wait until every selected change is archived, then reconcile the batch once.

Do not run this workflow during proposal or apply. Do not modify `docs/superpowers/` or the generated `openspec-archive-change` skill. 

## Workflow

1. Resolve the repository root and require `docs/documentation-governance.md` to exist.
2. Read that governance file completely before inspecting or editing documentation. Treat it as the detailed authority for ownership, new-document thresholds, deletion rules, validation, and output.
3. List every change in the completed archive batch. Read each archived proposal, design, specs, tasks, validation status, and the synced main specs.
4. Inspect the implemented code, resources, Manifest, build configuration, and tests affected by the batch. Do not infer current behavior from planning artifacts alone.
5. Merge the changes into one impact map. Deeply reconcile affected documents and perform the governance file's global lightweight checks.
6. Update, rewrite, merge, add, or delete documentation by ownership. Do not append a per-change history to long-lived documents or duplicate a complete rule across files.
7. If code and effective specs conflict, identify the cause. Do not hide the conflict by choosing whichever text is convenient. Stop for user direction when it cannot be resolved from repository evidence.
8. Run the governance file's required link, reference, OpenSpec, focused-test, frontmatter, and diff checks.
9. Return the exact `Documentation reconciliation` summary defined by the governance file. Explain every no-change category and every unverified item.

Do not claim the batch archive workflow is fully complete until reconciliation and validation finish, even though the changes have already moved into the archive.
