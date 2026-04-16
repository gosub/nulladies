# NullaDies [![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

*Nulla dies sine linea* — not a day without a line.

A micro-task tracker for Android. The idea is simple: completing many small things through the day builds positive momentum. Each completed task becomes a colored square on the day's grid — a visible record that you did something.

> This is a personal experiment, not intended for publication or use by anyone other than me. Development is suspended for now, but I may add or change things from time to time when I want to try something new.

## Features

- **Daily task grid** — colored squares, one per task; tap any square to select it, then mark it done or skip it
- **Recurring tasks** — four recurrence types: daily, specific days of the week, specific dates, or a queue slot (pops the next item from your FIFO todo queue)
- **FIFO todo queue** — a backlog of one-off tasks that feed into the day via queue-slot recurrences
- **Home screen actions** — mark a task Done or Skip (toggleable); split a queue-slot task into two
- **Quick-add FAB** — add a one-off task to the queue without leaving the home screen
- **Configurable day rollover** — new day generation fires at a time you set, triggered on app resume
- **Progress counter** — shows how many tasks are done out of today's total
- **Action log** — a history screen showing every action: completions, skips, splits, quick-adds
- **Org-mode export** — auto-writes a plain-text `.org` backup on every change to a folder you pick; read-only after setup
- **JSON backup** — manual export and import of all recurring tasks, queue, and settings as a JSON file; useful for device migration or full restore
- **Fully offline** — no network permissions, no accounts, no cloud

## Stack

- Plain Java (no Kotlin, no Jetpack Compose)
- Raw SQLite via `SQLiteOpenHelper` (no Room)
- XML layouts with `LinearLayout` / `RelativeLayout` / `FrameLayout`
- `AppCompatActivity` with Material3 theme and `BottomNavigationView`
- Gradle build system, Nix shell for the toolchain
- minSdk 26 (Android 8.0)

No IDE required. No Android Studio. Just a terminal and a text editor.

## Build

**Prerequisites:** either [Nix](https://nixos.org/download/) (provides JDK 17, Gradle, and the Android SDK automatically), or install JDK 17, Gradle, and the Android SDK yourself and set `ANDROID_HOME`.

```bash
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

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
