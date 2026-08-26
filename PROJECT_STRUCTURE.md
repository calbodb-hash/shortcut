# Short Cut — Project Architecture & Package Structure

This document outlines the Clean Architecture layers, package structure, and data flow for **Short Cut** (a high-performance, non-destructive short-form video editor for Android).

---

## 1. Clean Architecture Overview

Short Cut adheres to Clean Architecture principles with unidirectional data flow and strict layer boundaries:

```text
┌─────────────────────────────────────────────────────────────┐
│                    1. UI Layer (Compose)                    │
│   HomeScreen · EditorScreen · SettingsScreen · ExportDialog  │
└──────────────────────────────┬──────────────────────────────┘
                               │ Observes UI State / Emits User Events
                               ▼
┌─────────────────────────────────────────────────────────────┐
│               2. Presentation Layer (ViewModels)            │
│   HomeViewModel · EditorViewModel · ExportViewModel         │
└──────────────────────────────┬──────────────────────────────┘
                               │ Calls Domain Contracts / Dispatches Edits
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                   3. Domain Layer (Pure Kotlin)             │
│   Models · ITimelineEngine · IPreviewEngine · IExportEngine  │
│   TimelineHistory (Undo/Redo) · IProjectRepository          │
└──────────────┬───────────────────────────────┬──────────────┘
               │ Implements Contracts          │ Persists Data
               ▼                               ▼
┌──────────────────────────────┐ ┌────────────────────────────┐
│ 5. Media & Hardware Engine   │ │ 4. Data Layer (Room / JSON)│
│  MediaCodec · MediaPlayer    │ │  AppDatabase · ProjectDao  │
│  SmartCache · DeviceProfiler │ │  ProjectRepository         │
└──────────────────────────────┘ └────────────────────────────┘
```

---

## 2. Directory & Package Structure

```text
com.example
├── MainActivity.kt                               # Main entry point with Edge-to-Edge setup
│
├── core
│   └── performance
│       ├── DeviceProfiler.kt                     # Hardware detection (RAM, Cores, Low/Med/High/Ultra tier)
│       └── SmartCacheEngine.kt                   # LRU memory cache for thumbnails & audio waveforms
│
├── data
│   ├── local
│   │   ├── AppDatabase.kt                        # Room database instance
│   │   ├── Converters.kt                         # JSON type converters for timeline serialization
│   │   ├── ProjectDao.kt                         # DAO with reactive Coroutine Flow queries
│   │   └── ProjectEntity.kt                      # Room database table entity
│   └── repository
│       ├── IProjectRepository.kt                 # Repository interface definition
│       ├── ProjectRepository.kt                  # Repository implementation with Room
│       └── MediaPickerHelper.kt                  # Video metadata retriever & stock media generator
│
├── domain
│   ├── engine
│   │   ├── ITimelineEngine.kt                    # Timeline manipulation interface
│   │   ├── TimelineEngine.kt                     # Non-destructive split, trim, speed, color engine
│   │   ├── IPreviewEngine.kt                     # Real-time preview playback interface
│   │   ├── PreviewEngine.kt                      # Frame-accurate playback loop & audio sync
│   │   ├── IExportEngine.kt                      # Hardware export interface
│   │   └── ExportEngine.kt                       # MediaCodec encoder & MP4 muxer engine
│   └── model
│       ├── TimelineModels.kt                     # VideoClip, AudioClip, TextLayer, Transform, Adjustments
│       ├── ProjectModels.kt                      # Project, AspectRatio, TimelineHistory (Undo/Redo)
│       └── ExportModels.kt                       # ExportSettings, Resolution, FrameRate, Codec
│
├── presentation
│   ├── ui
│   │   ├── components
│   │   │   └── CommonComponents.kt               # Badges, buttons, sliders, dialogs
│   │   ├── navigation
│   │   │   └── NavGraph.kt                       # Type-safe Navigation Compose routes
│   │   └── screens
│   │       ├── home
│   │       │   └── HomeScreen.kt                 # Project grid, template presets, aspect ratio picker
│   │       ├── editor
│   │       │   ├── EditorScreen.kt               # Main workspace with preview & timeline integration
│   │       │   └── components
│   │       │       ├── VideoPreviewSurface.kt    # Render canvas with guidelines & color matrix
│   │       │       ├── TimelineView.kt           # Multi-track timeline, scrubber ruler, trim handles
│   │       │       ├── BottomEditorToolbar.kt    # Contextual bottom action toolbar
│   │       │       └── ToolPanels.kt             # Adjust, Speed, Filter, Audio, Text, Transform panels
│   │       ├── export
│   │       │   └── ExportDialog.kt               # Export configuration & live rendering progress
│   │       └── settings
│   │           └── SettingsScreen.kt             # Hardware diagnostics, cache management, proxy toggle
│   └── viewmodel
│       ├── HomeViewModel.kt                      # Project draft list & creation lifecycle
│       ├── EditorViewModel.kt                    # Active timeline editing, undo/redo, auto-save
│       ├── ExportViewModel.kt                    # Render progress & bitrate estimation
│       └── SettingsViewModel.kt                  # Hardware profiling & cache eviction
│
└── ui
    └── theme
        ├── Color.kt                              # Short Cut palette (Coral Red, Cyan, Violet, Dark Canvas)
        ├── Theme.kt                              # Studio Material 3 dark color scheme
        └── Type.kt                               # Studio typography definitions
```

---

## 3. Layer Descriptions

| Layer | Responsibility | Key Classes |
| :--- | :--- | :--- |
| **UI** | Declarative Jetpack Compose UI. Stateless composables observing `StateFlow` and emitting user intent callbacks. | `HomeScreen`, `EditorScreen`, `TimelineView`, `VideoPreviewSurface`, `SettingsScreen` |
| **Presentation** | Handles UI state holders, user input dispatching, undo/redo commands, and auto-save timers. | `EditorViewModel`, `HomeViewModel`, `ExportViewModel`, `SettingsViewModel` |
| **Domain** | Pure Kotlin business logic. Zero Android framework coupling. Houses data models, engine contracts, and the undo/redo stack. | `Timeline`, `VideoClip`, `TimelineHistory`, `ITimelineEngine`, `IPreviewEngine`, `IExportEngine` |
| **Data** | Offline-first persistence via Room Database with JSON serialization for complex timelines. | `AppDatabase`, `ProjectDao`, `ProjectEntity`, `ProjectRepository`, `MediaPickerHelper` |
| **Media Engine** | Hardware accelerated video encoding (`MediaCodec`), preview frame syncing, LRU thumbnail caching, and device profiling. | `TimelineEngine`, `PreviewEngine`, `ExportEngine`, `SmartCacheEngine`, `DeviceProfiler` |

---

## 4. Key Design Patterns

1. **Unidirectional Data Flow (UDF)**: ViewModels emit immutable `UiState` data classes; UI composables emit user intent lambdas.
2. **Dependency Inversion**: High-level modules (ViewModels) depend on domain interfaces (`ITimelineEngine`, `IProjectRepository`), allowing independent testing.
3. **Non-Destructive Editing Model**: Source video files are never modified. The project maintains an edit instruction tree executed at preview and export time.
4. **Command / State History**: All timeline mutations push previous states to `TimelineHistory` for undo/redo capability.
5. **Adaptive Hardware Tiers**: `DeviceProfiler` dynamically adjusts preview resolution and buffer sizes to match the device's RAM and CPU capabilities.
