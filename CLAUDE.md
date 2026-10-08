# VictorKeys

Android keyboard forked from FlorisBoard, tuned for coding and Linux use. Kotlin, Jetpack Compose, Gradle (Kotlin DSL).

## Layout
- `app/src/main/kotlin/dev/patrickgold/florisboard/` — app code (package name kept from upstream).
  - `ime/nlp/` — suggestion/spelling provider interfaces and `NlpManager`.
  - `ime/nlp/latin/LatinLanguageProvider.kt` — the active suggestion provider (prefix completion).
  - `app/AppPrefs.kt` — all preferences (`prefs.suggestion.*`, etc.).
  - `app/settings/typing/TypingScreen.kt` — suggestion settings UI.
- `app/src/main/assets/ime/dict/data.json` — English word -> frequency (max 255).
- `app/src/main/assets/ime/dict/dev.json` — coding/Linux word list (JSON array of strings).
- `app/src/main/res/values/strings.xml` — English strings; other locales come from Crowdin, do not edit them.

## Conventions
- Follow `.editorconfig` (4 spaces, 120 cols, LF; 2 spaces for JSON/YAML).
- New user-visible text goes in `values/strings.xml`, never hardcoded (except where a file already does so deliberately).
- Keep the Apache-2.0 header on new Kotlin files.

## Build / verify
- `./gradlew :app:assembleDebug`, `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug`.
- The Android Gradle plugin must be resolvable (needs network); the cloud sandbox may not be able to build.

## Dictionary notes
- Dev words keep their original casing via `displayForms`; lookup keys are lowercase.
- Words may contain `-` or `_`; `LatinLanguageProvider.determineLocalComposing` keeps these together (`apt-get`, `snake_case`). Words containing `.`, `/` or `:` (e.g. `/etc`, `std::string`) are still split by the word-break rules.
- Learned words persist to `filesDir/nlp/learned_words.json`; blocked words to `blocked_words.json`.

## Roadmap (phases)
1. CLAUDE.md — done
2. Persist learned/blocked words — done
3. Handle `-`/`_` words — done
4. Settings toggle for dev dictionary (`prefs.suggestion.devDictionaryEnabled`) — done
