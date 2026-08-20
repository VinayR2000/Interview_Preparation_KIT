# Git & GitHub — Fundamentals to Production Workflows

## Theory

### What is Git?
A distributed version control system. Every developer has a full copy of the repository history. Tracks changes, enables collaboration, supports branching and merging.

### Core Concepts

| Concept | Description |
|---------|-------------|
| Repository | Project directory tracked by Git |
| Commit | Snapshot of changes (immutable, has SHA hash) |
| Branch | Movable pointer to a commit (lightweight) |
| HEAD | Pointer to current branch/commit |
| Staging Area (Index) | Area where you prepare commits |
| Remote | Server copy of repo (GitHub, GitLab, Bitbucket) |
| Clone | Copy remote repo locally |
| Fork | Personal copy of someone else's repo (GitHub concept) |

---

## Internal Working

### Git Object Model ⭐⭐

```
Git stores 4 types of objects:
├── Blob → File content (no filename, just content hash)
├── Tree → Directory listing (maps filenames to blobs/trees)
├── Commit → Snapshot (points to tree + parent commit + metadata)
└── Tag → Named reference to a commit

Commit structure:
commit abc123
├── tree: def456 (root directory snapshot)
├── parent: xyz789 (previous commit)
├── author: John <john@company.com>
├── committer: John <john@company.com>
├── message: "Add order validation logic"
└── SHA: computed from all above
```

### The Three Areas ⭐⭐⭐

```
Working Directory          Staging Area (Index)         Repository (.git)
(your files)               (next commit preview)        (committed history)
     │                           │                            │
     │── git add ──────────────► │                            │
     │                           │── git commit ────────────► │
     │◄── git checkout ─────────────────────────────────────  │
     │                           │                            │
     │── git diff ──► (shows unstaged changes)                │
     │                           │── git diff --staged ──►    │
```

---

## Branching Strategies ⭐⭐⭐

### Git Flow

```
main (production)
 │
 ├── develop (integration branch)
 │    │
 │    ├── feature/order-validation (short-lived)
 │    │    └── Merge back to develop when done
 │    │
 │    ├── feature/payment-retry
 │    │    └── Merge back to develop when done
 │    │
 │    └── release/v2.1.0 (stabilization)
 │         ├── Bug fixes only
 │         └── Merge to main + develop when ready
 │
 └── hotfix/fix-payment-bug (from main)
      └── Merge to main + develop

Best for: Teams with scheduled releases, enterprise software
```

### GitHub Flow (Simplified) ⭐⭐⭐

```
main (always deployable)
 │
 ├── feature/add-caching
 │    ├── Developer works on branch
 │    ├── Opens Pull Request
 │    ├── Code review + CI passes
 │    └── Merge to main → Auto-deploy
 │
 ├── fix/race-condition
 │    ├── Developer works on branch
 │    ├── Opens Pull Request
 │    ├── Code review + CI passes
 │    └── Merge to main → Auto-deploy
 │
 └── (main is always production-ready)

Best for: Continuous deployment, small teams, SaaS
```

### Trunk-Based Development

```
main (trunk)
 │
 ├── Short-lived branches (< 1-2 days)
 │    └── Merge frequently (daily or more)
 │
 ├── Feature flags control what's visible to users
 │
 └── Everyone commits to main (or very short branches)

Best for: High-performing teams, CI/CD maturity, Google/Facebook style
```

### Which Strategy for Microservices? ⭐⭐⭐

```
Recommended: GitHub Flow or Trunk-Based

Why:
├── Each microservice has its own repo (or folder in monorepo)
├── Independent deployment per service
├── Short-lived branches (feature → PR → merge → deploy)
├── Feature flags for in-progress features
└── CI/CD pipeline triggers on merge to main
```

---

## Merge vs Rebase ⭐⭐⭐

### Merge

```
Before merge:
main:    A---B---C
              \
feature:       D---E

After git merge feature:
main:    A---B---C---F (merge commit)
              \     /
feature:       D---E

Pros: Preserves full history, non-destructive
Cons: Creates merge commits, history can look complex
```

### Rebase

```
Before rebase:
main:    A---B---C
              \
feature:       D---E

After git rebase main (on feature branch):
main:    A---B---C
                  \
feature:           D'---E' (new commits, re-applied)

After fast-forward merge:
main:    A---B---C---D'---E'

Pros: Clean linear history, no merge commits
Cons: Rewrites history (don't rebase shared branches!)
```

### When to Use Which ⭐⭐⭐

| Scenario | Use |
|----------|-----|
| Updating feature branch with latest main | Rebase (keeps your commits on top) |
| Merging feature branch into main | Merge (PR merge) or Squash merge |
| Shared branch (multiple people working) | Merge (never rebase shared branches) |
| Cleaning up local commits before PR | Interactive rebase (`git rebase -i`) |
| Production hotfix | Merge (preserve traceability) |

### Squash Merge (Common in PRs)

```
Feature branch has 15 messy commits:
"WIP", "fix typo", "actually fix", "revert", "try again"

Squash merge into main:
main: A---B---C---D (single clean commit with all changes)

Benefit: Clean main history, messy development is fine
```

---

## Pull Request Workflow ⭐⭐⭐

```
1. Create branch from main
   git checkout -b feature/order-caching

2. Make changes, commit frequently
   git add .
   git commit -m "Add Redis caching for order lookups"

3. Push to remote
   git push -u origin feature/order-caching

4. Open Pull Request (GitHub/GitLab)
   ├── Title: Clear, concise description
   ├── Description: What, why, how, testing done
   ├── Reviewers: Assign 1-2 reviewers
   ├── Labels: feature, backend, needs-review
   └── Link: Related Jira ticket / issue

5. CI Pipeline runs automatically
   ├── Build
   ├── Unit tests
   ├── Integration tests
   ├── Linting
   └── Security scan

6. Code Review
   ├── Reviewer provides feedback
   ├── Author addresses comments
   └── Approval granted

7. Merge (squash merge to main)

8. Auto-deploy triggers (CI/CD)

9. Delete feature branch
```

### Good PR Practices

| Practice | Description |
|----------|-------------|
| Small PRs | < 400 lines changed. Easier to review, faster to merge |
| Clear description | What changed, why, how to test, screenshots if UI |
| Self-review first | Review your own diff before requesting others |
| One concern per PR | Don't mix refactoring with feature work |
| Tests included | PR should include tests for new code |
| CI must pass | Never merge with failing checks |
| Respond promptly | Don't let PRs sit for days |

---

## Essential Git Commands ⭐⭐⭐

### Daily Workflow
```bash
# Start new work
git checkout main
git pull origin main
git checkout -b feature/my-feature

# Work and commit
git add -p                    # Stage interactively (review each change)
git commit -m "Add feature"  # Commit with message

# Stay up to date
git fetch origin
git rebase origin/main       # Put your commits on top of latest main

# Push and create PR
git push -u origin feature/my-feature
```

### Fixing Mistakes
```bash
# Undo last commit (keep changes staged)
git reset --soft HEAD~1

# Undo last commit (keep changes unstaged)
git reset HEAD~1

# Undo last commit (discard changes — DANGEROUS)
git reset --hard HEAD~1

# Amend last commit (change message or add files)
git add forgotten-file.java
git commit --amend -m "Better message"

# Undo a specific commit (create new reverse commit)
git revert abc123

# Stash changes temporarily
git stash
git stash pop                 # Re-apply stashed changes
```

### Investigation
```bash
# See who changed a line
git blame src/OrderService.java

# Search commit history
git log --oneline --grep="payment"

# Find when a bug was introduced
git bisect start
git bisect bad                # Current commit is bad
git bisect good v2.0.0        # This version was good
# Git will binary search for the breaking commit

# See what changed between branches
git diff main..feature/my-branch
git log main..feature/my-branch --oneline
```

### Collaboration
```bash
# Cherry-pick a specific commit from another branch
git cherry-pick abc123

# Clean up local branches (deleted on remote)
git fetch --prune
git branch -d feature/old-branch

# Interactive rebase (clean up commits before PR)
git rebase -i HEAD~5
# Options: pick, squash, fixup, reword, drop
```

---

## .gitignore for Java/Spring Boot

```gitignore
# Build
target/
build/
*.jar
*.war

# IDE
.idea/
*.iml
.vscode/
.settings/
.project
.classpath

# Environment
.env
*.env.local
application-local.yml

# OS
.DS_Store
Thumbs.db

# Logs
*.log
logs/

# Dependencies (if not needed)
node_modules/
```

---

## Git in CI/CD ⭐⭐⭐

```
Git Event                    CI/CD Response
├── Push to feature branch → Run build + unit tests
├── PR opened/updated      → Run full test suite + code analysis
├── PR merged to main      → Build → Docker → Deploy to staging
├── Tag created (v2.1.0)   → Build → Docker → Deploy to production
└── Hotfix branch merged   → Fast-track: Build → Deploy to production
```

### Conventional Commits
```
Format: <type>(<scope>): <description>

Types:
├── feat: New feature (triggers minor version bump)
├── fix: Bug fix (triggers patch version bump)
├── docs: Documentation only
├── refactor: Code restructuring (no behavior change)
├── test: Adding/fixing tests
├── chore: Build/tooling changes
├── perf: Performance improvement
└── BREAKING CHANGE: in footer (triggers major version bump)

Examples:
feat(orders): add caching for order lookups
fix(payment): prevent double-charge on retry
refactor(auth): extract JWT validation to shared library
```

---

## Interview Questions

### Q: Merge vs Rebase — when do you use each?
**A:**
- **Rebase**: Use to update your feature branch with latest main (keeps linear history). Use interactive rebase to clean up commits before opening a PR. NEVER rebase a branch others are working on.
- **Merge**: Use for merging PRs into main (preserves the history that a feature was developed in parallel). Use merge commits for production deployments (traceability).
- **Squash merge**: Use when feature branch has messy commits and you want one clean commit on main.

### Q: What branching strategy do you use?
**A:** For microservices with CI/CD, I use GitHub Flow:
- `main` is always deployable
- Short-lived feature branches (1-3 days max)
- PR with code review and CI checks before merge
- Squash merge for clean history
- Feature flags for in-progress features that aren't ready for users
- Auto-deploy on merge to main

### Q: How do you handle merge conflicts?
**A:**
1. Pull latest main into my branch (`git rebase origin/main`)
2. Resolve conflicts in IDE (understand both sides, don't blindly accept)
3. If conflict is complex, communicate with the other author
4. Run tests after resolution to ensure nothing broke
5. Prevention: small PRs, frequent merges, clear code ownership per area

### Q: How do you recover from a bad production deploy via Git?
**A:**
- **Revert**: `git revert <commit>` → creates a new commit that undoes the change. Safe, preserves history.
- **Don't**: `git reset --hard` on shared branches (rewrites history, breaks everyone).
- After revert: investigate, fix properly on a new branch, re-deploy.
- Better prevention: feature flags, canary deployments, automated rollback in CI/CD.

### Q: What makes a good commit message?
**A:** Conventional commits format: `type(scope): description`. The message should answer "if applied, this commit will..." without needing to read the diff. Good: `fix(payment): prevent duplicate charges during retry timeout`. Bad: `fix bug`, `WIP`, `update`.
