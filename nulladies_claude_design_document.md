# Nulla Dies — Design Document
*Nulla dies sine linea*

*Personal agency and habit tracking app for Android*

---

## 1. Concept

DayFlow is a minimal Android app combining a habit tracker and todo list. The core insight is that completing many small tasks throughout the day — including tiny habits like drinking water or making the bed — creates positive psychological momentum. Rather than a scrolling list, tasks are represented as a **grid of colored squares**, keeping the full day visible at a glance.

---

## 2. Core Data Model

### 2.1 Task

All tasks, whether recurring or one-off, share the same flat structure:

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Unique identifier |
| `title` | String | Display name |
| `color` | Enum | One of 8 fixed palette colors |

There is no hierarchy. Subtasks do not exist. Splitting a task destroys the original and creates two new sibling tasks at the same queue position.

### 2.2 Recurring Task

A recurring task extends the base task with a recurrence rule:

- **Daily** — appears every day
- **Days of week** — appears on selected days (e.g. Mon/Wed/Fri)
- **Specific dates** — appears on a fixed list of calendar dates

One special recurring task type is a **Queue Slot** — a placeholder that pulls the next item from the todo queue when the day is generated. Multiple queue slots can exist in the recurring list.

### 2.3 Todo Queue

A flat FIFO list of one-off tasks. Tasks are added to the tail and consumed from the head when a Queue Slot is resolved at day start. Tasks have `id`, `title`, and `color`.

### 2.4 Daily Instance

At day rollover, a concrete list of tasks is generated for the day by:
1. Walking the recurring task list in order
2. Filtering by recurrence rule (does this task apply today?)
3. Replacing each Queue Slot with the next item from the todo queue

This daily instance is immutable once generated — changes to the recurring list or queue do not affect the current day.

### 2.5 Action Log

Every user interaction is recorded as an event with a UTC timestamp:

- `COMPLETED` — task marked done
- `SKIPPED` — task marked "not today"
- `PUSHED` — task pushed down in today's order
- `QUICK_ADD` — task added to todo queue
- `SPLIT` — task split into two (records both new task titles)
- `DAY_START` — day rollover event, records full task list for the day

---

## 3. Home Screen

The home screen is the only screen the user needs during normal daily use.

### 3.1 Layout (top to bottom)

```
┌─────────────────────────────┐
│  COMPLETED (compact grid)   │
├─────────────────────────────┤
│  CURRENT TASK LINE          │
├─────────────────────────────┤
│  REMAINING TASKS (grid)     │
└─────────────────────────────┘
```

**At day start** (nothing completed yet), the completed section is hidden and the full grid fills the screen.

**As tasks are completed**, the compact completed grid grows at the top and the remaining grid shrinks below.

### 3.2 Completed Section (compact grid)

- Smaller squares than the remaining grid
- Each square retains its task color
- No labels — pure visual density indicator
- Acts as a progress record, not an action area

### 3.3 Current Task Line

A single highlighted row showing the next task to act on. Contains:

- Task title
- Action buttons: **Done**, **Not Today**, **Push Down**, **Split** (if applicable)

**Done** — marks complete, task moves to compact grid, next task becomes current.

**Not Today** — task is skipped, moves to bottom of remaining grid, styled in gray. Stays visible but deprioritized.

**Push Down** — soft snooze. Task moves 2 positions down in the remaining grid. Does not skip the task.

**Split** — opens a small dialog to enter two new task titles. Original task is destroyed; two new tasks are inserted at the same position in the queue.

### 3.4 Remaining Grid

- Fixed-size squares (size determined by testing)
- Color-coded by task color
- Skipped tasks appear at the bottom, grayed out
- Tapping a square promotes it to the Current Task Line (user can manually pick)
- Grid scrolls vertically if tasks exceed screen height

### 3.5 Quick Add Button

A floating action button (FAB) on the home screen. Tapping opens a minimal bottom sheet with:

- Text field for task title
- Color picker (fixed palette, 8 swatches)
- Confirm button

The task is appended to the tail of the todo queue. It does not immediately appear in today's grid unless a Queue Slot is still unresolved.

---

## 4. Color Palette

A fixed set of 8 visually distinct colors, chosen for mutual discernibility in both light and dark mode. The app assigns no semantic meaning to colors — the user builds their own mental model.

Suggested palette (adjust for contrast testing):

| Name | Light mode hex | 
|---|---|
| Red | `#E53935` |
| Orange | `#FB8C00` |
| Yellow | `#FDD835` |
| Green | `#43A047` |
| Teal | `#00897B` |
| Blue | `#1E88E5` |
| Purple | `#8E24AA` |
| Gray | `#757575` |

---

## 5. Secondary Screens

### 5.1 Todo Queue View

Accessible from the home screen via a nav button. Shows the full FIFO queue as a list. Features:

- Reorder items via drag handle
- Add new item (same flow as quick add)
- Delete item
- Split item (same as home screen split action)
- Visual indicator of which items are Queue Slots' "next up"

### 5.2 Recurring Tasks View

Accessible from settings or nav. Shows the ordered list of recurring tasks. Features:

- Reorder via drag handle
- Add recurring task (title, color, recurrence rule)
- Add Queue Slot placeholder
- Edit or delete existing tasks

### 5.3 Settings

- **Day rollover time** — time picker, default 00:00
- **Backup folder** — folder picker for org-mode file location

---

## 6. Day Rollover

At the user-configured rollover time:

1. A `DAY_START` event is written to the action log
2. The new daily task list is generated from recurring tasks + queue
3. The home screen resets: completed section hidden, fresh grid shown
4. Uncompleted and skipped tasks from the previous day are discarded (not rolled over)

If the app is not open at rollover time, the rollover happens on next app open if the current time is past the configured rollover time and the last `DAY_START` event was from a previous day.

---

## 7. Data Persistence

### 7.1 Local Storage

SQLite database stored in the app's private data directory. Tables:

- `recurring_tasks` — ordered list of recurring tasks and queue slots
- `todo_queue` — ordered FIFO queue
- `daily_tasks` — today's generated task list with state (pending/completed/skipped)
- `action_log` — append-only event log with timestamps

### 7.2 Org-mode Backup

On every state change, the app rewrites a single `.org` file to a user-selected folder. The app always overwrites — it never reads this file back. It is a write-only export.

File structure:

```org
#+TITLE: Nulla Dies Backup
#+DATE: [2026-03-01]

* Config
** Recurring Tasks
- [ ] Drink water :daily: :blue:
- [ ] Exercise :mon,wed,fri: :green:
- [ ] [[QUEUE_SLOT]] :orange:

** Todo Queue
- [ ] Write blog post :teal:
- [ ] Fix login bug :red:

* Action Log
** 2026-03-01
- [2026-03-01 08:12] DAY_START
- [2026-03-01 08:15] COMPLETED "Drink water"
- [2026-03-01 09:03] PUSHED "Exercise"
- [2026-03-01 09:45] COMPLETED "Exercise"
- [2026-03-01 10:10] SKIPPED "Read news"
```

---

## 8. Technical Stack

- **Language**: Java
- **UI**: Jetpack Compose (minimal, system default light/dark theme)
- **Database**: Room (SQLite wrapper)
- **Build system**: Gradle, managed via Nix for reproducible CLI builds (no Android Studio)
- **Min SDK**: Android 8.0 (API 26) — covers >95% of active devices
- **No network permissions** — fully offline

---

## 9. Out of Scope for v1

- Notifications or reminders
- Cloud sync
- Task recurrence patterns beyond daily / days-of-week / specific dates
- Subtask hierarchy (splitting is flat and destructive)
- Statistics or analytics views
- Widgets
- Themes beyond system light/dark

---

## 10. Open Questions for Future Versions

- Should the grid squares resize dynamically when task count is high?
- Should skipped tasks roll over to tomorrow as candidates (not forced)?
- Should the todo queue support priorities beyond FIFO order?
- Should split tasks inherit the parent's color automatically?
