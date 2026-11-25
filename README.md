<div align="center">

<img src=".github/repo_icon.png" alt="VictorKeys logo" width="128">

# VictorKeys

**An Android keyboard forked from FlorisBoard, with coding and Linux word suggestions.**

Works on its own; fleet integration is optional

<p>
  <img src="https://img.shields.io/badge/Release-no_release_yet-FF2BD6?style=flat-square&labelColor=0B0F1A" alt="No release yet" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-FF2BD6?style=flat-square&labelColor=0B0F1A" alt="License: Apache 2.0" /></a>
  <a href="https://github.com/Victorious93/VictorKeys/actions/workflows/android.yml"><img src="https://img.shields.io/github/actions/workflow/status/Victorious93/VictorKeys/android.yml?style=flat-square&label=CI&labelColor=0B0F1A&color=00E5FF" alt="CI" /></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B_(API_26)-00E5FF?style=flat-square&labelColor=0B0F1A" alt="Android 8.0+" />
  <a href="https://github.com/florisboard/florisboard"><img src="https://img.shields.io/badge/based_on-FlorisBoard-00E5FF?style=flat-square&labelColor=0B0F1A" alt="Based on FlorisBoard" /></a>
  <a href="https://victorious-framework.pages.dev"><img src="https://img.shields.io/badge/Fleet-Victorious_Framework-FF2BD6?style=flat-square&labelColor=0B0F1A" alt="Victorious Framework fleet" /></a>
</p>

</div>

**Package id:** `dev.patrickgold.florisboard` (display name is VictorKeys). VictorKeys and upstream FlorisBoard cannot be installed on the same device.

> **Credits:** VictorKeys is a modified version of [FlorisBoard](https://github.com/florisboard/florisboard) by [patrickgold](https://github.com/patrickgold) and [the FlorisBoard contributors](https://github.com/florisboard/florisboard/graphs/contributors) (Apache-2.0). This project is not affiliated with or endorsed by the upstream authors.

## Features

Fork-specific additions in this repository:

- **Coding & Linux dictionary** — a built-in word list (`app/src/main/assets/ime/dict/dev.json`) with a Settings toggle labelled **Coding & Linux dictionary**
- **Identifier-aware composing** — hyphen- or underscore-joined identifiers are treated as a single composing word
- **Persistent suggestions** — learned and blocked suggestion words are saved to disk

The rest of the keyboard is FlorisBoard (currently versioned `0.6.0-alpha02` in `gradle.properties`): themes, clipboard history, emoji keyboard, and extension support as implemented upstream. This README does not repeat upstream's feature list or claim device-test results.

## Screenshots

No screenshots of VictorKeys are in this repository yet.

## Install / download

No GitHub Release and no published APK yet. Do not install FlorisBoard from F-Droid or Google Play and expect it to be this fork.

Debug / pre-release builds can be assembled from source (below). These are not finished software. Install only if you understand the risk.

## Build from source

Requires the Android SDK (compile/target SDK 37, minSdk 26) and the Gradle wrapper in this repo.

```bash
./gradlew clean && ./gradlew assembleDebug
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for upstream build notes that still apply.

## Permissions

This fork inherits FlorisBoard's permission set. See the [upstream permissions page](https://docs.florisboard.org/permissions).

## Victorious fleet

VictorKeys is part of the [Victorious Framework](https://victorious-framework.pages.dev), a family of Android and PC tools by Don Victor. **It works standalone; fleet integrations are optional.** Related: [Victorious-Keyboard](https://github.com/Victorious93/Victorious-Keyboard), [VictorSuite](https://github.com/Victorious93/VictorSuite), [VictorLinux](https://github.com/Victorious93/VictorLinux). The roster is in [victorious-fleet](https://github.com/Victorious93/victorious-fleet) and live status is in [fleet-manifest](https://github.com/Victorious93/fleet-manifest).

## Contributing

Issues and pull requests are welcome. Please describe what you changed and why, and keep documentation accurate to the code — do not invent features, builds, or test results. Upstream contribution guidelines are in [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).

```
Copyright 2020-2026 The FlorisBoard Contributors
```

VictorKeys modifications in this fork follow the same license. Upstream trademarks and product names remain their owners'.

Thanks to [The FlorisBoard Contributors](https://github.com/florisboard/florisboard/graphs/contributors) for making the upstream project possible. The original FlorisBoard app icon was designed by [Nikolay Anzarov](https://www.behance.net/nikolayanzarov) ([@BloodRaven0](https://github.com/BloodRaven0)); this fork uses a new launcher icon added in the VictorKeys rename commit.
