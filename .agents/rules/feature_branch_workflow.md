---
description: Feature Branch Completion and Git Operations Workflow
always_on: true
---

# Feature Branch Completion & Git Workflow Standard

Whenever completing work on a feature branch or finishing a feature implementation:

1. **Pull Request (PR) Creation**: On every feature branch completion, open a Pull Request targeting the primary upstream branch (`origin/main`).
2. **Merge Operation**: Complete the merge operation into `origin` (e.g., via `gh pr merge` or approved project merge strategy).
3. **Remote Origin Synchronization & Lag Verification**: Verify that the local workspace and remote `origin` are fully synchronized with 0 lag (`git rev-list --left-right --count origin/main...main`), ensuring that no unpushed or unmerged commits remain unaligned.
