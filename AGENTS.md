# Global Coding & Documentation Standards

Whenever writing, modifying, refactoring, or generating code across all projects and languages, strictly adhere to the following documentation standards so that any subsequent developer or autonomous AI agent can immediately understand each component, codeblock, and variable:

## 1. Universal Documentation Standard
- **Every File & Module**: Include a header explaining the file's architectural role, component relationships, and lifecycle.
- **Every Class, Struct & Interface**: Provide clear documentation specifying its single responsibility, architectural layer, lifecycle, and thread-safety / concurrency model.
- **Every Function & Method**:
  - Purpose and description of business logic and side effects.
  - Parameter annotations (`@param` in KDoc/JSDoc/TSDoc/Javadoc, `:param` in Sphinx, etc.) with explicit units of measurement (e.g., seconds vs. milliseconds, Hz, normalized 0.0f..1.0f, radians, pixels).
  - Return annotations (`@return`) detailing data semantics, nullability, or reactive stream behavior (`Flow`, `Observable`, `Promise`).
  - Error/Exception annotations (`@throws`) documenting expected failure modes.
- **Every Key Variable, Property & Constant**: Document mutable backing state vs. public immutable exposures, default values, flags, and hardware/system handles.
- **Non-Trivial Codeblocks**: Add inline comments explaining algorithmic steps, mathematical formulas, state machine transitions, hardware APIs, and time drift compensation.

## 2. Language-Specific Conventions
- **Kotlin**: Full KDoc format with Markdown support, `@param`, `@return`, `@throws`, `@see`, `@property`.
- **TypeScript / JavaScript**: Full TSDoc / JSDoc standard with `@param`, `@returns`, `@throws`.
- **Python**: Google Style or NumPy docstrings with Types, Args, Returns, Raises, and Units.
- **Java / Swift**: Standard Javadoc / Swift-Doc with explicit parameter and return specifications.

## 3. Feature Branch Lifecycle & Remote Synchronization Standard
- **Pull Request on Feature Completion**: On every feature branch completion, always open a Pull Request (PR) targeting the primary tracking branch (`origin/main`).
- **Merge Operation**: Complete the merge operation into `origin` (e.g. via `gh pr merge` or project-specified merge strategy).
- **Origin Synchronization & Lag Verification**: Verify that the local workspace and remote `origin` are fully synchronized with 0 lag (`git rev-list --left-right --count origin/main...main`), ensuring no unpushed or unmerged commits remain adrift.
