# SelfUp

An Android app that turns real daily actions into quests, quests into XP, and XP into stat levels.

## Layout

- `core/` holds the game rules in plain Kotlin: stats, XP split, level curve, ranks, penalties, quest schedules and the quest-day boundary. It has no Android dependencies and is its own Gradle build, so it builds and tests anywhere with a JDK.
- `app/` is the Android app: Jetpack Compose UI and the Room database. Earned XP is an append-only ledger (`xp_event`), and levels are always derived from it.

## Build

```sh
./gradlew :core:test               # game rules, JDK only
./gradlew :app:testDebugUnitTest   # database and repository (Robolectric)
./gradlew :app:assembleDebug       # APK
```

CI runs all three on every pull request and uploads the debug APK as a build artifact.
