# Workspace Agent Rules

## Feature Branch Isolation
- Always create a dedicated git branch (for example, `feat/<feature-name>`, `fix/<issue-name>`, or `chore/<task-name>`) from the base branch before implementing any new feature, fix, or change so every piece of work stays isolated per branch until reviewed and merged.
- Never develop new features directly on `main`.

## Feature Map Registry
- Use the `feature-map` skill ([SKILL.md](.agents/skills/feature-map/SKILL.md)) whenever proposing, designing, implementing, modifying, or verifying mobile app modules, screens, utilities, or features.
- Keep [docs/FEATURE_MAP.md](docs/FEATURE_MAP.md) updated with pre-implementation checks and post-implementation verification statuses.
