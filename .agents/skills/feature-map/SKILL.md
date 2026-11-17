---
name: feature-map
description: Maintain and verify a Markdown feature map (`docs/FEATURE_MAP.md`) for mobile application projects. Use whenever proposing, designing, implementing, modifying, or verifying new app modules, screens, utilities, or features to prevent duplicate functionality, validate integration completeness, and keep the feature registry accurate.
---
# Feature Map

Track all mobile application modules and features in a central Markdown table (`docs/FEATURE_MAP.md`) and enforce pre-build and post-build verification whenever new features are added.

## When to Use

- Before implementing a new mobile app module, screen, service, or utility (pre-implementation check)
- After building or updating a feature to verify registration, dependencies, and tests (post-implementation verification)
- When auditing existing app capabilities or updating feature statuses in `docs/FEATURE_MAP.md`

## Feature Map Schema

Maintain `docs/FEATURE_MAP.md` using the following Markdown table structure. Create the file with this template if it does not exist yet.

| ID | Feature / Module | Category | Entry / Route | Key Files | Dependencies & Permissions | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| FM-001 | Auth & Session | Core | `/auth/login` | `src/features/auth/*` | SecureStore, Firebase Auth | verified |

### Status Definitions

- `planned`: Scoped and approved, not yet implemented
- `in-progress`: Actively being built or refactored
- `unverified`: Code written, awaiting route, permission, and test verification
- `verified`: Integrated, tested, and confirmed working

## Workflow

### Step 1: Pre-Implementation Check (Before Coding)

Run this check before writing code for any new feature or module:

1. Read `docs/FEATURE_MAP.md` (or scan existing feature directories if the map is missing and initialize it first).
2. Compare the proposed feature against existing rows for overlapping routes, duplicate utilities, or shared state/services.
3. If an overlap exists, decide whether to extend the existing module or keep them distinct, and document the boundary.
4. Add a new row with the next sequential ID (`FM-XXX`) and set Status to `planned` or `in-progress`.

### Step 2: Post-Implementation Verification (After Coding)

Before marking a new feature complete, verify every item below:

1. **Navigation & Entry Points**: Confirm the screen, tab, drawer item, or deep link is registered in the app navigator and reachable by users.
2. **Dependencies & Permissions**: Confirm any required packages, native modules, API keys, or OS permissions (camera, location, storage, notifications) are declared and listed in the table.
3. **Boundary & Regression Check**: Verify the new module does not break or shadow routes, state stores, or services owned by existing `verified` features.
4. **Tests & Build**: Run unit/integration tests or typechecks for the touched modules and confirm clean execution.

### Step 3: Update the Feature Map

1. Update the feature's row in `docs/FEATURE_MAP.md` with accurate `Entry / Route`, `Key Files`, and `Dependencies & Permissions`.
2. Change `Status` from `in-progress` to `verified` only after all Step 2 checks pass (otherwise mark `unverified` and note what remains).

## Gotchas

- Never mark a feature `verified` if its screen exists in `src/` but is not wired into navigation or entry routes.
- Keep `Key Files` concise using directory globs (e.g., `src/features/scanner/*`) rather than listing every helper file.
- Always check for existing shared utilities (auth, storage, API clients, UI primitives) in the map before creating new ones.