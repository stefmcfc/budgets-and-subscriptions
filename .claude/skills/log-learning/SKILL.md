---
name: log-learning
description: Capture a non-obvious, generalizable lesson from this session into .claude/learnings.md. Use when the user explicitly asks to update/log/note a learning ("update learnings", "log this as a learning", "remember that for next time"), or clearly signals something just discovered is worth preserving ("that's surprising", "good to know", "future me should remember this"). Not for routine end-of-AC reviews (see backend-code-reviewer) and not something to fire automatically after every fix.
---

# Logging a learning

`.claude/learnings.md` is a curated set of notes-to-self about things that weren't obvious going
in while hand-coding the backend — grouped by topic, not chronological. Its value is in being
high-signal: every entry should save future-you from rediscovering the same gotcha the hard way.
This skill's job is to add to it well, not to log everything that happens.

**Trigger deliberately, not reflexively.** Unlike `backend-code-reviewer`, this isn't meant to
fire automatically at every checkpoint. If asked to "update learnings" generically (no specific
insight named), look back over the session yourself and propose candidates — don't pad the file
with routine fixes just to have produced something.

## Steps

1. **Filter for what's actually generalizable.** Ask: would this save real time if it happened
   again in a different context, or is it a one-off typo/oversight specific to this exact line?
   A fixed compile error is only worth logging if *why* it happened reveals something non-obvious
   (an API's argument order changed, a framework quirk, a subtle equality/mocking trap) — not
   every red-to-green fix qualifies. If nothing from the session clears this bar, say so instead
   of forcing an entry.
2. **Read the current `.claude/learnings.md` first.** Check whether this fits as a new topic
   section or belongs folded into an existing one (e.g. another JWT-secret or Spock-mocking entry).
   Don't duplicate a topic that's already there — extend it instead.
3. **Match the established format exactly:**
   ```markdown
   ## <short, specific heading naming the actual gotcha>

   **Context**: which AC/file/task surfaced this.

   **Why**: the actual mechanism — not just what broke, but why, in enough detail that the
   reasoning transfers to a future unrelated situation.

   **Fix** (or a practical takeaway, if there's no single fix): what to do differently next time.
   ```
   A short illustrative snippet (a line or two of Groovy/Java) is fine to anchor the explanation,
   the same way existing entries do — never a full real implementation.
4. **Append via `Edit`**, placed near related existing topics rather than always at the end, and
   tell the user what you added in 1-2 sentences — don't restate the whole entry back to them,
   they can read the file.

## What NOT to do

- Don't log something just because the session touched it — only genuinely non-obvious,
  reusable lessons belong here.
- Don't rewrite or reorganise unrelated existing sections while adding a new one.
- Don't write real production code or real test content into an entry — illustrative fragments
  only, consistent with the file's existing style.
- Don't treat this as a substitute for `backend-code-reviewer` — reviewing finished work and
  recording a durable lesson from it are different tasks; this skill only does the latter.
