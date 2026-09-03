# LostRat fork

I needed an updated version for myself, for my own app.

- Set minimum API to 23. Compiler should automatically exclude code that is exclusively for API below 23.
- Updated gradle stuff for version 8.
- Used gemini cli to aid in updating deprecations in osmdroid-android module.
- Updated osmdroid-mapsforge module and the OpenStreetMapsViewer sample project that uses it. Both now use MapsForge version 0.25.0. Included my tiny forest park .map for testing.

I can only hope that the other features survive the updates. I do not use any other modules.

## Using the fork in your app

The fork is published through [JitPack](https://jitpack.io/#LostRat/osmdroid-lostrat), which builds
straight from this GitHub repository. Add the JitPack repository once:

```groovy
// settings.gradle(.kts) -> dependencyResolutionManagement.repositories
maven { url = uri("https://jitpack.io") }
```

JitPack only serves three kinds of version, and the version string is **not** the `pom.version` in
`gradle.properties`:

| Version string | What you get |
|---|---|
| `v7.0.1-lostrat` (a git tag) | Fixed, reproducible. Use this. |
| `<40-char commit hash>` or its short form | That exact commit. Useful to try a fix before it is tagged. |
| `master-SNAPSHOT` | Latest push to `master`. Moves under you; JitPack may cache it for a day. |

There is no `7.0.2-lostrat-SNAPSHOT` on JitPack. That string is the in-repo `pom.version` and only
exists in your **local** Maven repository after you build it yourself:

```bash
./gradlew clean build publishToMavenLocal
```

```groovy
// then, with mavenLocal() in your repositories (note the plain com.github.lostrat group):
implementation("com.github.lostrat:osmdroid-android:7.0.2-lostrat-SNAPSHOT")
implementation("com.github.lostrat:osmdroid-mapsforge:7.0.2-lostrat-SNAPSHOT")
implementation("com.github.lostrat:osmdroid-geopackage:7.0.2-lostrat-SNAPSHOT")
```

The [TestOsmdroidLostRat](https://github.com/LostRat/Test-Osmdroid-LostRat) app consumes the fork this way while a release is being prepared, then switches to the tag.

July 1, 2026 note — **tagged release** `v7.0.1-lostrat`:

The `v7.0.1-lostrat` part is based on a git tag set for a specific commit.
```groovy
implementation("com.github.LostRat.osmdroid-lostrat:osmdroid-android:v7.0.1-lostrat")
implementation("com.github.LostRat.osmdroid-lostrat:osmdroid-mapsforge:v7.0.1-lostrat")
implementation("com.github.LostRat.osmdroid-lostrat:osmdroid-geopackage:v7.0.1-lostrat")
```

The JitPack build notes includes:
```text
✅ Build artifacts:
com.github.LostRat.osmdroid-lostrat:osmdroid-mapsforge:v7.0.1-lostrat
com.github.LostRat.osmdroid-lostrat:osmdroid-server-jdk:v7.0.1-lostrat
com.github.LostRat.osmdroid-lostrat:osmdroid-shape:v7.0.1-lostrat
com.github.LostRat.osmdroid-lostrat:osmdroid-geopackage:v7.0.1-lostrat
com.github.LostRat.osmdroid-lostrat:OSMMapTilePackager:v7.0.1-lostrat
com.github.LostRat.osmdroid-lostrat:osmdroid-android:v7.0.1-lostrat
com.github.LostRat.osmdroid-lostrat:osmdroid-wms:v7.0.1-lostrat
```

**Unreleased changes on `master`** (adds `MapsForgeTileCacheKeys` and fixes MapsForge tile cache read/write pairing, see `docs/CHANGELOG-lostrat.md`) can be pulled from JitPack ahead of the next tag:
```groovy
implementation("com.github.LostRat.osmdroid-lostrat:osmdroid-android:master-SNAPSHOT")
implementation("com.github.LostRat.osmdroid-lostrat:osmdroid-mapsforge:master-SNAPSHOT")
```

November 27, 2025 — previous release `v7.0.0-lostrat`:
```groovy
implementation("com.github.LostRat.osmdroid-lostrat:osmdroid-android:v7.0.0-lostrat")
implementation("com.github.LostRat.osmdroid-lostrat:osmdroid-mapsforge:v7.0.0-lostrat")
implementation("com.github.LostRat.osmdroid-lostrat:osmdroid-geopackage:v7.0.0-lostrat")
```

If you clone the repo you could then go back to an earlier commit before my AI aided changes from August 2025 forward. Additional documentation lives in the [`docs/`](docs/) folder (see below).

You can include a cloned OSMDroid folder inside your `settings.gradle` file: `includeBuild 'your/clone/path'`

## Documentation

| Document | Description |
|----------|-------------|
| [docs/CHANGELOG-lostrat.md](docs/CHANGELOG-lostrat.md) | Fork-specific changelog (Markdown) |
| [docs/CHANGELOG-lostrat.html](docs/CHANGELOG-lostrat.html) | Same changelog as HTML (readable in a browser) |
| [docs/ENHANCED_LAYER_SYSTEM.md](docs/ENHANCED_LAYER_SYSTEM.md) | 10-layer overlay z-order and tap-priority system (reference) |
| [docs/LAYERING_SYSTEM_TUTORIAL.md](docs/LAYERING_SYSTEM_TUTORIAL.md) | Tutorial for the layer system: use cases, manual overrides, migration from stock osmdroid |
| [docs/DENSITY_SCALING_USAGE.md](docs/DENSITY_SCALING_USAGE.md) | `DisplayDensityManager` and `applyDensityScaling()` usage guide for MapsForge and overlays |
| [docs/ADVANCED_POLYLINE_TUTORIAL.md](docs/ADVANCED_POLYLINE_TUTORIAL.md) | Multi-colour polylines with the `ColorMapping*` classes (speed, elevation, ranges) |
| [docs/16KB_PAGE_SIZE_COMPLIANCE.md](docs/16KB_PAGE_SIZE_COMPLIANCE.md) | Google Play 16 KB page-size requirement: what was changed and fallback options |
| [docs/SQLTILEWRITER_NULL_SAFETY.md](docs/SQLTILEWRITER_NULL_SAFETY.md) | Why tile caching degrades gracefully instead of crashing when SQLite is unavailable |
| [docs/2026-06-14-display-layer-optimization-report.html](docs/2026-06-14-display-layer-optimization-report.html) | Audit of the draw path (findings F1 to F6, which the June/July 2026 perf commits implement) |
| [docs/2026-09-03-change-survey-and-commit-plan.html](docs/2026-09-03-change-survey-and-commit-plan.html) | Survey of unpushed 7.0.2 changes, untracked-file triage, and commit plan for the fork and the test app |
| [reference/](reference/) | Session notes and reference code that is **not** compiled or maintained (see its README) |

### Interactive overlays (markers & polylines)

See **[docs/ENHANCED_LAYER_SYSTEM.md](docs/ENHANCED_LAYER_SYSTEM.md)** for how draw order and tap handling work with the enhanced layer system — decoration vs interactive markers, user-drawn lines on top, FolderOverlay flattening, and manual overrides.

**Tip for AI-assisted coding:** When using Cursor, Copilot, Gemini CLI, or similar tools, point the assistant at `docs/ENHANCED_LAYER_SYSTEM.md` (or add it to your project rules) when you need help making polylines and markers interactive or fixing overlay tap/draw-order issues.

**Note:** once upon a time this `includeBuild` method had worked for me for MapsForge

See the mapsforge sample for getting setting a scaling factor to get it all to look right.

If a dependency is not updated at the time of this push to github it is for a reason. Identifying which ones cannot yet be updated is a challenge all by itself.

For example:
`androidx.core:core:1.16.0` is not set to 17 to allow compileSdk = 35 rather than requiring at least 36

---
# osmdroid [![Build Status](https://api.travis-ci.org/osmdroid/osmdroid.svg?branch=master)](https://travis-ci.org/osmdroid/osmdroid) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.osmdroid/osmdroid-android/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.osmdroid/osmdroid-android) [![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-osmdroid-brightgreen.svg?style=flat)](https://android-arsenal.com/details/1/279) [![SourceSpy Dashboard](https://sourcespy.com/shield.svg)](https://sourcespy.com/github/osmdroidosmdroid/)

osmdroid is a (almost) full/free replacement for Android's MapView (v1 API) class. It also includes a modular tile provider system with support for numerous online and offline tile sources and overlay support with built-in overlays for plotting icons, tracking location, and drawing shapes.

<a href="https://f-droid.org/packages/org.osmdroid/">
<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="90"></a>
<a href="https://play.google.com/store/apps/details?id=org.osmdroid">
<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Get it on F-Droid" height="90"></a>

Current Release: **6.1.20 Aug 18, 2024**

Current Development version: 6.1.21-SNAPSHOT

Next Release Version (planned): 6.1.21

Note: check your calendar, it may take up to a few days for all global mirrors to update.

Please read the [osmdroid wiki](https://github.com/osmdroid/osmdroid/wiki) for  tutorials on integration.

**Gradle dependency**
```groovy
repositories {
        mavenCentral()
}

dependencies {
    implementation 'org.osmdroid:osmdroid-android:<VERSION>'
}
```

Be sure to replace `<VERSION>` with the last release version above.


**Maven dependency**
```xml
<dependency>
  <groupId>org.osmdroid</groupId>
  <artifactId>osmdroid-android</artifactId>
  <version><VERSION></version>
  <type>aar</type>
</dependency>
```

**Platform or API Level (API level 8 = Platform 2.2)**

Note: this just assumes you need just osmdroid-android. Other modules require higher min SDK levels.

```xml
<platform>8</platform>
```
You can also [compile osmdroid from source](https://github.com/osmdroid/osmdroid/wiki/How-to-build-osmdroid-from-source) or [download the dependency directly from OSS](https://oss.sonatype.org/content/groups/public/org/osmdroid/osmdroid-android/) or [download the distribution package](https://github.com/osmdroid/osmdroid/releases)

## Want the latest and greatest?

We periodically publish snapshots to maven central. 
If you're interesting in trying it out, using the following:

```groovy
repositories {
    mavenCentral()
    maven{
        url  'https://oss.sonatype.org/content/repositories/snapshots/'
        name 'OSS-Sonatype'
    }
}
dependencies {
    implementation 'org.osmdroid:osmdroid-android:<VERSION>-SNAPSHOT:debug@aar'
}
```

Use at your own risk though, it may not be stable or may have bugs or performance issues.
If you run across any, please report them.

In case gradle doesn't resolve it, it can be download manually here:
https://oss.sonatype.org/service/local/repositories/snapshots/content/org/osmdroid/osmdroid-android/<VERSION>-SNAPSHOT/osmdroid-android-<VERSION>-SNAPSHOT.aar

Side note: gradle's cached dependencies and doesn't really handle snapshot very well.
To force gradle to update snapshots on every build, try adding this to your root `build.gradle` file.

```groovy
allprojects  {
  // forces all changing dependencies (i.e. SNAPSHOTs) to automagicially download
    configurations.all {
        resolutionStrategy {
            cacheChangingModulesFor 0, 'seconds'
    }
}
```

You can also build your project using the gradle option `--refreshDependencies`

## OK now what?

Continue reading here, [How-to-use-the-osmdroid-library](https://github.com/osmdroid/osmdroid/wiki)

Related and **important** wiki articles
 * [Change Log](https://github.com/osmdroid/osmdroid/wiki/Changelog)
 * [FAQ](https://github.com/osmdroid/osmdroid/wiki/FAQ)
 * [Important notes on using osmdroid in your app](https://github.com/osmdroid/osmdroid/wiki/Important-notes-on-using-osmdroid-in-your-app)
 * [Upgrade guide](https://github.com/osmdroid/osmdroid/wiki/Upgrade-Guide)

## I have a question or want to report a bug

If you have a question, please view the [osmdroid FAQ](https://github.com/osmdroid/osmdroid/wiki/FAQ).  
You can also view the [Stack Overflow osmdroid tag](http://stackoverflow.com/questions/tagged/osmdroid) and [osmdroid Google Group](https://groups.google.com/forum/#!forum/osmdroid) where you can get feedback from a large pool of osmdroid users.

If you still have an issue, please check the [Changelog](https://github.com/osmdroid/osmdroid/wiki/Changelog) page to see if this issue is fixed in a newer or upcoming version of osmdroid.

If think you have a legitimate bug to report then go to the [Issues](https://github.com/osmdroid/osmdroid/issues?state=open) page to see if your issue has been reported. If your issue already exists then please contribute information that will help us track down the source of the issue. If your issue does not exist then create a new issue report. When creating an issue, please include the version of osmdroid, the Android platform target and test device you are using, and a detailed description of the problem with relevant code. It is particularly helpful if you can reproduce the problem using our [OpenStreetMapViewer](https://github.com/osmdroid/osmdroid/tree/master/OpenStreetMapViewer) sample project as your starting point.

## I want to contribute

See our [contributing guide](https://github.com/osmdroid/osmdroid/blob/master/CONTRIBUTING.md) 

For your reference, the [dashboard](https://sourcespy.com/github/osmdroidosmdroid/) provides a high level overview of the repository including structure of [UI classes](https://sourcespy.com/github/osmdroidosmdroid/xx-ouiswing-.html), [module dependencies](https://sourcespy.com/github/osmdroidosmdroid/xx-omodulesc-.html), [external libraries](https://sourcespy.com/github/osmdroidosmdroid/xx-ojavalibs-.html), and other components of the system.

## I want more!

The [OSMBonusPack project](https://github.com/MKergall/osmbonuspack) adds additional functionality for use with osmdroid projects.

## Screenshots

![](images/MyLocation.png)
![](images/CustomLayer.png)
![](images/TwoMarkers.png)

## Demo Videos

[Free Draw](https://youtu.be/b119xU1UCXs)

[Maps Forge](https://youtu.be/xXCr_bLebMk)

[Floating point zoom](https://youtu.be/YBjjjLPuFdM)

[IIS Tracker](https://youtu.be/Jw8z1ke9Idk)

## Building from source for editing osmdroid 

JDK11+ is required
Gradle 7.4.2 is what we are currently using
Android Studio Bumblebee
(latest supported configuration as of May 2022)

```
./gradlew clean build
```

Then you can install and test the app using normal command line utils.

Or just open Android studio.

## Building from source and using the aar's in your app

JDK11+ is required
Gradle 7.4.2 is what we are currently using
(latest supported configuration as of May 2022)

We recommend building from the command line.


```
./gradlew clean build publishToMavenLocal
```

In **your** root `build.gradle` file, add mavenLocal() if not present.
```
allprojects {
    repositories {
            mavenCentral()
            mavenLocal()    //add this if it's missing
    }
}

```

Then in your APK or AAR project that needs osmdroid.

```
    implementation 'org.osmdroid:osmdroid-android:<VERSION>-SNAPSHOT:debug@aar'
```
Where VERSION is the version listed as the value for `pom.version` in osmdroid's `gradle.properties`. Note that when using the release versions from Maven Central, drop the `:debug@aar` part. When using a "release" version that you build locally with gradle, you'll need `:debug@aar` instead.


# Support

osmdroid is entirely community supported. There is no corporate sponsorship. No full time employees, no paid employees.
It's all volunteer support, if that. If you see a problem, feel free to report, fix it and open a pull request. 
You have access to 100% of the source code. Maps are **hard** to get right and this library isn't perfect.

Please don't complain about slow response times or lack of support. You will be banned. No warnings, no second chances.

We do NOT provide map data, map tiles, imagery, etc. That is all provided by a map tile ~~source~~ that you select or that you provide.
Please don't complain about a map source showing you the wrong data. It's not the fault of this library. 
