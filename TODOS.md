# TODOS

## Auth / Authorization

### Club management write endpoint + ADMIN authorization

**What:** Add the actual club_management mutation endpoint(s) to `ClubRestController.kt` (currently no PUT/POST/PATCH/DELETE exists) and attach `@PreAuthorize("hasRole('ADMIN')")`.

**Why:** The `users.role` + JWT-claim + `GrantedAuthority` infrastructure (this branch) has nothing to protect until this lands — a role column with no enforced boundary is dead weight. Ship this soon after the infra PR so the gap window (role exists, nothing checks it) stays short.

**Context:** Infra PR (feature/deploy, 2026-09-06 office-hours + eng-review) deliberately scoped out any real endpoint — `ClubRestController.kt` only has read endpoints today. The auth chain to reuse: `JwtAuthenticationFilter` now populates `Authentication.authorities` from the JWT's `role` claim (`ROLE_ADMIN` / `ROLE_USER`), and `SecurityConfig` now carries `@EnableMethodSecurity`, so `hasRole('ADMIN')` will work directly once this endpoint exists. See design doc `~/.gstack/projects/olkkani-lol-view-back/jin-feature-deploy-club-admin-role-design-20260906-005608.md`.

**Effort:** S
**Priority:** P1
**Depends on:** Infra PR (users.role column, JWT claim, JwtAuthenticationFilter authorities) landing first.
