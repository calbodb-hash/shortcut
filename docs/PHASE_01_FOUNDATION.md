# Short Cut — Phase 01: Architectural Foundation & Persistence

## 1. Overview & Objective

Phase 01 focused on establishing the clean, scalable foundation for **Short Cut**. The objective was to engineer a robust, testable Android architecture adhering to Clean Architecture principles, implementing local Room database persistence, creating the studio design system, and providing hardware diagnostics without blocking the Android UI thread.

---

## 2. Completed Architecture & Structure

The codebase was divided into strictly decoupled layers:

```text
com.example
├── core/performance/               # Hardware profiling & LRU cache engine
│   ├── DeviceProfiler.kt           # Hardware capability scoring (Low, Mid, High, Ultra)
│   └── SmartCacheEngine.kt         # In-memory LRU cache for frame thumbnails
│
├── data/
│   ├── local/                      # Room database & type converters
│   │   ├── AppDatabase.kt          # Room Database definition
│   │   ├── Converters.kt           # JSON serialization for complex timeline models
│   │   ├── ProjectDao.kt           # Reactive Coroutines Flow queries
│   │   └── ProjectEntity.kt        # Project table definition
│   └── repository/
│       ├── IProjectRepository.kt   # Repository domain contract
│       ├── ProjectRepository.kt    # Implementation backed by Room DAO
│       └── MediaPickerHelper.kt    # Video metadata parser & sample media factory
│
├── domain/model/                   # Pure Kotlin data models (Zero Android dependencies)
│   ├── ProjectModels.kt            # Project, AspectRatio, MediaType
│   ├── TimelineModels.kt           # Timeline, VideoClip, AudioClip, TextLayer, Transform, Adjustments
│   └── ExportModels.kt             # ExportSettings, Resolution, FrameRate, Codec
│
├── presentation/
│   ├── navigation/                 # Type-safe Navigation Compose routes
│   │   └── NavGraph.kt
│   ├── ui/
│   │   ├── components/             # Reusable UI primitives (TimecodeBadge, ActionCard, Dialogs)
│   │   ├── screens/
│   │   │   ├── home/HomeScreen.kt  # Project drafts, aspect ratio modal, template hub
│   │   │   └── settings/SettingsScreen.kt # Hardware diagnostics & cache manager
│   │   └── theme/                  # Studio dark theme, palette & typography
│   └── viewmodel/
│       ├── HomeViewModel.kt        # Project creation & draft management
│       └── SettingsViewModel.kt    # Hardware tier profiling & cache eviction
```

---

## 3. Key Accomplishments & Deliverables

### A. Pure Domain Models
* **`Project`**: Core entity containing ID, user title, aspect ratio (`RATIO_9_16`, `RATIO_16_9`, `RATIO_1_1`, `RATIO_4_5`), creation and modification timestamps, and an embedded `Timeline`.
* **`Timeline`**: Root non-destructive container holding immutable lists of `VideoClip`, `AudioClip`, and `TextLayer` items along with computed `totalDurationMs`.
* **`VideoClip`**: Video and image segment model with `sourceUri`, `sourceDurationMs`, `sourceStartTimeMs`, `sourceEndTimeMs`, `timelineStartMs`, `speed`, `volume`, `transform`, `adjustments`, and `filter`.

### B. Room Database Persistence Layer
* **`ProjectEntity` & `ProjectDao`**: Room entity storing project metadata with full SQLite indexing.
* **`Converters`**: Type converters leveraging `org.json` serialization for zero-overhead JSON packing of nested timelines, clips, text layers, and adjustments.
* **`ProjectRepository`**: Asynchronous CRUD operations with reactive `Flow<List<Project>>` streaming updates to ViewModels.

### C. Studio Visual Identity & Design System
* **Dark Studio Palette**: High-contrast, eye-safe dark editing canvas (`#0A0B10`, `#141622`, `#1E2235`, `#2A2F45`) paired with vivid accents:
  * **Short Cut Coral Red** (`#FF3B5C`) for primary destructive / record actions
  * **Short Cut Cyan** (`#00E5FF`) for selection boundaries and timeline guides
  * **Short Cut Violet** (`#7C4DFF`) for audio layers
  * **Short Cut Amber** (`#FFAB00`) for text overlays and markers
  * **Short Cut Green** (`#00E676`) for success indicators and freeze states
* **Material 3 Theming**: Centralized typography scale, button styles, card shapes, and surface elevations.

### D. Home Dashboard & Creation Hub
* **Project Drafts Grid**: Displays recent projects with aspect ratio badges, duration timecodes, and last-modified dates.
* **New Project Modal**: Interactive modal with visual aspect ratio cards (9:16 vertical, 16:9 widescreen, 1:1 square, 4:5 social).
* **Project Actions**: One-tap rename dialog, project duplication, and deletion with confirmation safeguards.

### E. Hardware Profiling & Smart Caching
* **`DeviceProfiler`**: Inspects available RAM (`ActivityManager.MemoryInfo`), CPU hardware concurrency (`Runtime.availableProcessors()`), and classifies devices into `LOW`, `MEDIUM`, `HIGH`, or `ULTRA` performance tiers.
* **`SmartCacheEngine`**: Bounded LRU cache for memory-efficient frame caching with explicit clear and trim capabilities.
* **Settings & Diagnostics Screen**: Displays real-time device specifications, active memory consumption, and cache maintenance controls.

---

## 4. Verification & Testing
* Unit tests verifying Project Room entity serialization and deserialization.
* `DeviceProfiler` tier evaluation tests for various hardware profiles.
* Clean compile verification under Kotlin Gradle Plugin and Jetpack Compose.
