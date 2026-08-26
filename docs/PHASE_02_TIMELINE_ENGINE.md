# Short Cut — Phase 02: Multi-Track Timeline Engine & Playback System

## 1. Overview & Objective

Phase 02 established the heart of the video editor: the **Multi-Track Timeline Engine** and the **Frame-Accurate Preview & Scrubbing Architecture**. All editing operations were engineered as pure, non-destructive Kotlin transformations with zero frame-copying or disk rewriting, backed by an unlimited Undo/Redo command stack and responsive Compose UI.

---

## 2. Completed Architecture & Structure

```text
com.example
├── domain/
│   ├── engine/
│   │   ├── ITimelineEngine.kt        # Interface contract for pure timeline logic
│   │   ├── TimelineEngine.kt         # Implementation: split, trim, ripple, reorder
│   │   ├── IPreviewEngine.kt         # Interface contract for playback loop
│   │   └── PreviewEngine.kt          # Coroutine-based frame-accurate playback ticker
│   └── model/
│       ├── TimelineModels.kt         # Track models, clip boundaries, snap points
│       └── ProjectModels.kt          # TimelineHistory (Undo/Redo stack)
│
└── presentation/
    ├── ui/screens/editor/
    │   ├── EditorScreen.kt           # Primary editing studio layout
    │   └── components/
    │       ├── TimelineView.kt       # Multi-track timeline, ruler & trim handles
    │       ├── VideoPreviewSurface.kt# Preview viewport with aspect ratio framing
    │       └── BottomEditorToolbar.kt# Primary editing action bar
    └── viewmodel/
        └── EditorViewModel.kt        # State machine managing timeline, history & auto-save
```

---

## 3. Key Accomplishments & Deliverables

### A. Non-Destructive Timeline Engine (`ITimelineEngine`)
* **Razor Split Operation**:
  * Splits the target clip at exact playhead timestamp without modifying original files.
  * Calculates relative source offset based on playback speed: `sourceSplit = sourceStartTime + (relTime * speed)`.
  * Generates two sibling clips preserving all transforms, filters, and color adjustments.
* **Edge Trimming (In/Out Point)**:
  * Trims clip head (`trimClipStart`) or tail (`trimClipEnd`) with strict enforcement of minimum clip duration (`MIN_CLIP_DURATION_MS = 200ms`).
  * Clamps bounds within source media duration limits.
  * Recalculates `timelineStartMs` for all subsequent downstream clips (automatic ripple effect).
* **Ripple Delete & Reorder**:
  * Deletes any selected clip and automatically shifts subsequent clips forward to eliminate timeline gaps.
  * Provides reordering capability (`reorderClips(fromIndex, toIndex)`).
* **Track Duration Computation**:
  * Automatically keeps `timeline.totalDurationMs` synchronized to the longest active track.

### B. Frame-Accurate Playback & Scrubbing (`IPreviewEngine`)
* **Coroutine-Driven Playback Loop**: High-precision 60 FPS playback loop syncing playhead position to actual wall-clock elapsed time via `SystemClock.elapsedRealtime()`.
* **Dynamic Active Clip Lookup**: `Timeline.getActiveClipAt(playheadMs)` dynamically determines the visible clip and relative frame offset at any millisecond.
* **Seamless Looping & Boundaries**: Automatically halts or loops playback upon reaching the end of the timeline.

### C. Studio Timeline Composable (`TimelineView.kt`)
* **Zoomable Timecode Ruler**:
  * Dynamic scale factor (`msPerPixel` ranging from 10ms/px to 100ms/px).
  * Major and minor tick marks with human-readable timecodes (`MM:SS:CS`).
* **Interactive Red Playhead**: Draggable scrubber needle with glowing indicator and millisecond accuracy.
* **Multi-Track Visualization**:
  * **Video Track**: Thumbnail sequence strips, active clip highlight border (Short Cut Cyan), clip title, speed tag, and duration label.
  * **Interactive Trim Handles**: Left and right draggable bracket handles with real-time feedback.
  * **Audio Track**: Violet card headers with volume labels and simulated audio waveform bars.
  * **Text Track**: Amber badges indicating subtitle/caption duration and timeline positioning.
* **Magnetic Snapping**: Subtle snap alignment to clip start/end boundaries and playhead position during scrubbing.

### D. Undo / Redo Command History (`TimelineHistory`)
* **Immutable Snapshot Stack**: `TimelineHistory` maintains distinct undo and redo lists of immutable `Timeline` instances.
* **State Push & Branch Pruning**: Pushing a new mutation automatically clears redo history to preserve linear causality.
* **Full Coverage**: Every split, trim, delete, reorder, and text addition integrates directly into the undo/redo stack.

### E. Debounced Project Auto-Save
* **Non-Blocking Persistence**: Background coroutine job in `EditorViewModel` debounces state changes by 1.5 seconds and asynchronously writes to Room Database without causing UI frame drops.

---

## 4. Verification & Testing
* Extensive unit tests in `TimelineEngineTest.kt`:
  * Split calculations and duration verification.
  * Start and end trimming with minimum boundary clamping.
  * Ripple deletion and downstream timeline start shifting.
  * Undo and Redo stack state transitions and empty stack resilience.
* Robolectric integration tests verifying `EditorViewModel` initialization and auto-save triggering.
