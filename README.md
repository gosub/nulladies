# NullaDies [![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

*Nulla dies sine linea* — not a day without a line.

A micro-task tracker for Android. The idea is simple: completing many small things through the day builds positive momentum. Each completed task becomes a colored square on the day's grid — a visible record that you did something.

## Features

- **Daily task grid** — colored squares, one per task; the grid fills as you work through the day
- **Recurring tasks** — four recurrence types: daily, specific days of the week, specific dates, or a queue slot (pops the next item from your FIFO todo queue)
- **FIFO todo queue** — a backlog of one-off tasks that feed into the day via queue-slot recurrences
- **Home screen actions** — mark a task Done, skip it (Not Today), push it down the list, or split it into two
- **Promote** — tap any square in the grid to bring that task back to the current position
- **Quick-add FAB** — add a one-off task to today without touching the recurring list
- **Configurable day rollover** — new day generation fires at a time you set, triggered on app resume
- **Org-mode export** — writes a plain-text `.org` backup to a folder you choose via the system file picker; read-only after setup, no continuous access required
- **Fully offline** — no network permissions, no accounts, no cloud

## Stack

- Plain Java (no Kotlin, no Jetpack Compose)
- Raw SQLite via `SQLiteOpenHelper` (no Room)
- XML layouts with `LinearLayout` / `RelativeLayout` / `FrameLayout`
- Standard `android.app.Activity` (no AndroidX)
- Gradle build system, Nix shell for the toolchain
- minSdk 26 (Android 8.0)

No IDE required. No Android Studio. Just a terminal and a text editor.

## Build

**Prerequisite:** [Nix](https://nixos.org/download/) must be installed.

```bash
# Enter the dev shell (provides JDK 17, Gradle, Android SDK)
nix-shell

# Build debug APK
make

# Install on a connected device
make install

# Build, install, and launch
make run

# Stream filtered logs
make logcat
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.
