## Communication Style
- Be brief and concise in all responses
- Avoid unnecessary explanations

## Git Workflow
- This repo uses a bare repo + git worktree layout (`.bare`, with `main`/`develop`/feature worktrees as siblings).
- `feature/*` branches are always branched from `develop`, not `main`.
- When a skill (e.g. `finishing-a-development-branch`) asks whether to merge into `main`, merge into `develop` instead unless the user explicitly says `main`.

## Skill routing

When the user's request matches an available skill, invoke it via the Skill tool. When in doubt, invoke the skill.

Key routing rules:
- Product ideas/brainstorming → invoke /office-hours
- Strategy/scope → invoke /plan-ceo-review
- Architecture → invoke /plan-eng-review
- Design system/plan review → invoke /design-consultation or /plan-design-review
- Full review pipeline → invoke /autoplan
- Bugs/errors → invoke /investigate
- QA/testing site behavior → invoke /qa or /qa-only
- Code review/diff check → invoke /review
- Visual polish → invoke /design-review
- Ship/deploy/PR → invoke /ship or /land-and-deploy
- Save progress → invoke /context-save
- Resume context → invoke /context-restore
- Author a backlog-ready spec/issue → invoke /spec
