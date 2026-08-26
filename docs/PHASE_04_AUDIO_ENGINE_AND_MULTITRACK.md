# SHORT CUT — PHASE 04 DOCUMENTATION
## Professional Audio Engine, Multi-Track Audio Timeline & Audio Editing

### Overview
Phase 04 establishes a comprehensive, high-performance audio engine and multi-track audio editing suite engineered specifically for short-form video creation (Shorts, Reels, TikTok). It enables creators to seamlessly mix video sound, background music, voice-overs, and sound effects with sub-millisecond sync, non-destructive editing, real-time waveform visualization, and low memory overhead on mid-range and low-end Android devices.

---

### Core Audio Architecture

```text
┌────────────────────────────────────────────────────────────┐
│                    EditorViewModel                         │
│   (audioMixState, activeAudioClip, track operations)       │
└──────────────────────────────┬─────────────────────────────┘
                               │
       ┌───────────────────────┼────────────────────────┐
       ▼                       ▼                        ▼
┌──────────────┐      ┌─────────────────┐     ┌──────────────────┐
│TimelineEngine│      │AudioPreviewMixer│     │AudioWaveformEng. │
│(Data/Edits)  │      │(Multi-Player)   │     │(Peaks Cache)     │
└──────────────┘      └─────────────────┘     └──────────────────┘
       │                       │                        │
       ▼                       ▼                        ▼
┌──────────────┐      ┌─────────────────┐     ┌──────────────────┐
│  Timeline &  │      │ Media3 / Exo    │     │ TimelineWaveform │
│  AudioClip   │      │ Players Pool    │     │ Canvas Rendering │
└──────────────┘      └─────────────────┘     └──────────────────┘
```

---

### 1. Multi-Track Audio Data Model
- **`AudioTrackType`**:
  - `MUSIC` (Background soundtracks, ambient audio)
  - `VOICE_OVER` (Microphone recordings, voice narration)
  - `SFX` (Sound effects, transition whooshes, impacts, UI pops)
  - `EXTRACTED` (Audio separated/detached from video clips)
- **`AudioClip` Model**:
  - `id`, `trackId`, `title`, `sourceUri`, `audioTrackType`
  - `sourceStartTimeMs`, `sourceEndTimeMs`, `sourceDurationMs`
  - `timelineStartMs`, `timelineDurationMs`
  - `volume` (0.0x to 2.0x boost)
  - `speed` (0.1x to 10.0x with duration recalculation)
  - `fadeInMs`, `fadeOutMs` (Smooth millisecond fade ramps)
  - `pan` (-1.0 Left to +1.0 Right stereo panning)
  - `duckingEnabled`, `duckingLevel` (Smart background ducking during voice-over)
  - `isMuted` (Quick per-clip mute toggle)

---

### 2. Audio Engine Components
1. **`AudioWaveformEngine`**:
   - Asynchronous, non-blocking RMS peak extraction.
   - Dual-layer LRU in-memory and disk caching with automatic cache invalidation.
   - Downsampled peak bucketing to avoid high memory consumption.
2. **`AudioRecordingManager`**:
   - Hardware AAC encoder using `MediaRecorder` targeting 44.1kHz / 128kbps stereo.
   - 3-2-1 countdown sequence with live amplitude polling.
   - Real-time VU meter and dynamic waveform canvas visualizer.
   - Supports start, pause, resume, and non-blocking background file persistence.
3. **`AudioImportHelper`**:
   - Device audio scanner and `MediaMetadataRetriever` parser.
   - Detached audio extraction from existing video clips (`extractAudioFromVideo`).
   - Integrated royalty-free stock music and cinematic sound effects library (Lo-Fi, Upbeat, Cyber Synth, Whoosh, Impact, Shutter, Glitch, Bell).
4. **`AudioPreviewMixer`**:
   - Orchestrates multi-stream audio playback synchronized with video timeline playhead.
   - Dynamic volume leveling with live gain calculations for fade-in, fade-out, master volume, and smart voice-over ducking.
   - Peak level metering exposing `AudioMixState` (left/right RMS and clipping detection).

---

### 3. UI & Editing Features
- **Multi-Track Timeline**:
  - Distinct visual tracks for Music, Voice-over, and SFX with color-coded badges and waveforms.
  - Interactive clip selection, timeline snapping, and handle trimming.
- **Audio Editor Panel**:
  - Volume slider (0% to 200% with decibel readout).
  - Fade In & Fade Out millisecond steppers.
  - Playback Speed selector (0.5x, 1.0x, 1.25x, 1.5x, 2.0x).
  - Stereo Balance / Panning slider.
  - Smart Ducking toggle with attenuation control.
  - Split at Playhead, Duplicate, and Delete clip actions.
- **Voice-over Recording Studio**:
  - Live animated VU meter dialog with pause/resume and timeline alignment.
- **Audio Import Dialog**:
  - Tabbed picker for Device Storage, Royalty-Free Music Library, and Cinematic SFX.

---

### 4. Verification & Testing
- Automated unit test suite covering:
  - Audio clip split, trim, move, and duplicate operations.
  - Fade calculations, speed adjustments, and volume bounds clamping.
  - Multi-track timeline state transitions and undo/redo operations.
