# 13. Jobs & CronJobs

---

## Theory

**Jobs** run pods to completion (batch processing), while **CronJobs** create Jobs on a schedule.

### Job

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: db-migration
spec:
  completions: 1          # How many pods must complete successfully
  parallelism: 1          # How many pods run in parallel
  backoffLimit: 3         # Max retries before marking as failed
  activeDeadlineSeconds: 600  # Max runtime
  ttlSecondsAfterFinished: 300  # Auto-delete after completion
  template:
    spec:
      restartPolicy: Never    # or OnFailure
      containers:
      - name: migrate
        image: my-app:1.0
        command: ["./migrate", "--up"]
```

### Completion

```
completions: N means N pods must succeed

completions: 1, parallelism: 1 (default):
  Run one pod → success → Job complete

completions: 5, parallelism: 2:
  Run 2 pods at a time until 5 total succeed
  [pod1][pod2] → [pod3][pod4] → [pod5] → Job complete
```

### Parallelism

```
parallelism: How many pods run simultaneously

Examples:
  completions: 10, parallelism: 3
  → Run 3 pods at a time, until 10 complete successfully

  completions: 1, parallelism: 1
  → Single pod (default, sequential)

  completions: null, parallelism: 5 (work queue pattern)
  → Run 5 workers, complete when any pod succeeds
```

### Backoff

```
backoffLimit: Max pod failures before Job is marked as Failed

Default: 6
Backoff delay: exponential (10s, 20s, 40s... up to 6 min)

restartPolicy behavior:
  Never:     Create new Pod on failure (each failure counts)
  OnFailure: Restart container in same Pod (each restart counts)
```

### CronJob

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: daily-backup
spec:
  schedule: "0 2 * * *"           # 2 AM daily
  concurrencyPolicy: Forbid       # Don't run if previous still running
  successfulJobsHistoryLimit: 3   # Keep last 3 successful jobs
  failedJobsHistoryLimit: 1       # Keep last failed job
  startingDeadlineSeconds: 60     # Skip if missed by 60s
  jobTemplate:
    spec:
      template:
        spec:
          restartPolicy: OnFailure
          containers:
          - name: backup
            image: backup-tool:1.0
            command: ["./backup.sh"]
```

### Scheduling

```
Cron format: minute hour day-of-month month day-of-week

Examples:
  "*/5 * * * *"     Every 5 minutes
  "0 * * * *"       Every hour
  "0 2 * * *"       2 AM daily
  "0 0 * * 0"       Midnight every Sunday
  "0 0 1 * *"       First day of every month
```

### Concurrency Policy

```
concurrencyPolicy options:

Allow (default):  Multiple Jobs can run simultaneously
Forbid:          Skip new Job if previous is still running
Replace:         Cancel running Job, start new one

Best practice for most cases: Forbid
  (prevents overlapping backups, migrations, etc.)
```

### Job Cleanup

```
ttlSecondsAfterFinished: Auto-delete completed Jobs

ttlSecondsAfterFinished: 3600   # Delete 1 hour after completion
ttlSecondsAfterFinished: 0      # Delete immediately after completion

CronJob history limits:
  successfulJobsHistoryLimit: 3  # Keep last 3 successful
  failedJobsHistoryLimit: 1      # Keep last 1 failed
```

---

## Interview Questions

### Q1: What is the difference between Job and CronJob?

**A:** Job runs pods to completion once (batch task). CronJob creates Jobs on a schedule (recurring tasks). CronJob is to Job what crontab is to a shell script — it manages the scheduling and creates Job objects at specified intervals.

### Q2: What happens when a Job pod fails?

**A:** Depends on restartPolicy:
- `Never`: New Pod created (old pod kept for logs). Counts toward backoffLimit.
- `OnFailure`: Container restarted in same Pod. Counts toward backoffLimit.
After backoffLimit reached, Job marked as Failed. Exponential backoff between retries (10s, 20s, 40s... up to 6min).

### Q3: How do you handle a CronJob that takes longer than its schedule interval?

**A:** Use `concurrencyPolicy: Forbid` — skips the new run if previous is still executing. Alternatively, use `Replace` to cancel the running one and start fresh. Also set `activeDeadlineSeconds` on the Job template to timeout long-running jobs.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| restartPolicy: Always in Job | Job never completes | Use Never or OnFailure |
| No backoffLimit | Infinite retries | Set appropriate limit |
| CronJob with Allow concurrency | Overlapping jobs cause conflicts | Use Forbid |
| No ttlSecondsAfterFinished | Completed Jobs accumulate | Set TTL for cleanup |

---

## Best Practices

1. **Use `ttlSecondsAfterFinished`** for automatic cleanup
2. **Set `activeDeadlineSeconds`** to prevent runaway jobs
3. **Use `concurrencyPolicy: Forbid`** for non-idempotent operations
4. **Set `startingDeadlineSeconds`** to handle missed schedules
5. **Monitor Job failures** — alert on repeated failures
6. **Use `backoffLimit`** to prevent infinite retries

---

## Related Topics

- [04. Pods](./04-pods.md)
- [14. Scheduling](./14-scheduling.md)
- [28. Troubleshooting](./28-troubleshooting.md)
