# Short Cut — Product Vision, Capabilities & Roadmap

## 1. Executive Summary & Product Vision

**Short Cut** is a professional, high-performance, non-destructive short-form video editing application engineered specifically for Android. Built with Kotlin, Jetpack Compose, Material 3, and Clean Architecture, Short Cut is designed from the ground up to empower creators making content for:

* **YouTube Shorts** (9:16)
* **Instagram Reels** (9:16)
* **TikTok** (9:16)
* **Facebook Reels** (9:16)
* **Widescreen Video & Social Feeds** (16:9, 1:1, 4:5)
* **Gaming Highlights & Fast-Paced Montages**
* **Promotional Clips, Tutorials & Social Media Ads**

### Target Workload
The primary editing workload is **15–30 second high-density videos** packed with frequent cuts, multi-layer overlays, stylized typography, speed variations, color grading, precise audio timing, and dynamic framing.

---

## 2. Core Architectural Pillars

1. **Strict Non-Destructive Editing**: Original source video, audio, and image assets remain 100% untouched. All user modifications—splits, trims, crops, rotations, speed changes, and color adjustments—are stored as an immutable edit instruction tree rendered on-the-fly.
2. **Device Efficiency & Low-End Optimization**: Performance is a first-class citizen. Decoded frames are bounded, memory footprint is kept low via an LRU cache engine, and heavy media processing never blocks the Android UI thread.
3. **Adaptive Preview Quality**: Dynamic scaling (`LOW`, `BALANCED`, `HIGH`, `ULTRA`) based on detected hardware profile (RAM, CPU cores, thermal state), ensuring fluid playback without sacrificing final export fidelity.
4. **Offline-First Resilience**: All core workflows (editing, preview, persistence, timeline manipulation, filtering) function entirely offline without internet dependencies.
5. **Clean Architecture & Decoupled Design**: Strict isolation between Compose UI, Presentation (ViewModels), Domain (Pure Kotlin engines and models), Data (Room DB persistence), and Media/Hardware layers.

---

## 3. Current App Capabilities (Phases 1–3)

### A. Project & Workspace Management
* **Drafts & Multi-Project Hub**: Visual project card grid with auto-generated metadata, last-modified timestamps, and estimated project durations.
* **Canvas Aspect Ratio Presets**: Native support for **9:16** (Vertical Shorts/Reels/TikTok), **16:9** (Landscape/YouTube), **1:1** (Square), and **4:5** (Feed).
* **Automatic Background Persistence**: Asynchronous, debounced auto-saving via Room Database with JSON converters.
* **Project Operations**: Quick duplicate, rename, draft deletion, and aspect ratio re-framing.

### B. Multi-Track Timeline & Non-Destructive Editing
* **Multi-Track Stacking**: Independent parallel tracks for primary video clips, audio tracks, and customizable text layers.
* **Precision Scrubber & Zoomable Ruler**: Millisecond-accurate playhead scrubber, timecode badge display (`MM:SS:CS`), and dynamic timeline scaling (10ms/px to 100ms/px).
* **Non-Destructive Trimming**: Left/right edge handles with duration clamping and auto-ripple track recalculation.
* **Razor Split & Ripple Delete**: In-place split at current playhead and clean removal of segments with timeline gap elimination.
* **Track Reordering & Duplication**: Instant duplicate clip action and index re-sequencing.
* **Magnetic Snapping**: Snap-to-clip boundary and playhead alignment for precision cuts.
* **Robust Undo / Redo**: Multi-level state history tracking all destructive and non-destructive timeline modifications.

### C. Spatial Transforms & Direct Canvas Manipulation
* **2D Affine Transforms**: Continuous scale (0.2x to 5.0x), 360° rotation, and horizontal/vertical translations.
* **Axis Flips & Mirroring**: One-tap horizontal (X) and vertical (Y) mirror toggles.
* **Direct Multi-Touch Gestures**: Pinch-to-zoom, two-finger rotate, and drag-to-pan directly on the preview viewport with active bounding-box guides.
* **Framing Modes**: Fit (letterbox), Fill (bleed crop), and Original (1:1 pixel source).

### D. Interactive Crop Engine
* **Touch-Draggable Crop Boundaries**: Real-time bounding box adjustments with darkened exterior mask.
* **Rule-of-Thirds Composition Grid**: Cyan visual grid lines for professional composition alignment.
* **Aspect Presets**: 16:9, 9:16, 1:1, 4:5, and Freeform cropping modes.

### E. Color Grading & Parametric Adjustments
* **Live Color Matrix Engine**: Hardware-accelerated runtime `ColorMatrix` pipeline.
* **Adjustments**: Brightness (-100 to +100), Contrast (-100 to +100), Saturation (-100 to +100), Exposure (-100 to +100), Temperature (-100 to +100), Tint (-100 to +100), and Opacity (0.0 to 1.0).
* **Color Filter Presets**: Cinematic, Vintage, Cyberpunk, Warm, Cool, Noir (Black & White), Vivid, and Sunset presets.

### F. Non-Destructive Speed & Freeze Frames
* **Speed Multipliers**: Preset multipliers (0.25x, 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x, 3.0x, 4.0x) and continuous speed slider with real-time clip duration recalculation.
* **Freeze Frame Generator**: Instant creation of still image frames extracted at exact playhead timestamps with custom duration support (0.5s to 10s).

### G. Text Layering & Typography
* **Custom Text Overlays**: Text styling, font sizes, colors, background opacity, and timeline positioning with drag-and-drop layer selection.

### H. Hardware Diagnostics & Cache Engine
* **Device Profiler**: Dynamic assessment of device RAM, CPU cores, and classification into Low, Medium, High, or Ultra tiers.
* **Smart LRU Memory Cache**: Bounded cache for frame thumbnails, project assets, and memory eviction management.

---

## 4. Potential & Future Capabilities Roadmap

### Phase 4: Advanced Audio Engine & Sound Design
* **Multi-Channel Audio Mixing**: Independent volume control, mute toggles, and stereo balance per audio clip.
* **Audio Ducking**: Automatic background music volume reduction when speech/dialogue is detected.
* **Fade In / Fade Out**: Non-destructive audio envelope curves for smooth transitions.
* **Waveform Visualization**: Pre-rendered audio waveform bars in timeline tracks for beat-accurate cuts.
* **Voiceover Recording**: In-app microphone audio recording directly into dedicated audio tracks.

### Phase 5: Transitions & Video Effects Engine
* **Clip-to-Clip Transitions**: Dissolve, Fade to Black, Fade to White, Slide, Wipe, Zoom Blur, and Glitch transitions.
* **Dynamic Video Effects**: Shake, RGB Split, VHS distortion, Lens Flare, and Edge Glow.
* **Keyframe Animation Engine**: Position, scale, rotation, and opacity interpolation between custom keyframe markers.
* **Speed Curves (Speed Ramping)**: Bezier curve-based velocity ramping for viral sports and action edits (e.g. Montage / Hero ramping).

### Phase 6: AI-Assisted Creator Tools (On-Device First)
* **Auto-Captions & Subtitle Generator**: On-device speech-to-text generating synced word-by-word animated captions.
* **Silence & Filler Word Detection**: One-tap jump-cut generator that detects and eliminates speech pauses.
* **Beat Synchronization**: Automatic audio transient analysis placing visual timeline markers on music beats.
* **Background Removal / Smart Cutout**: AI segmentation of subjects without green screens.

### Phase 7: Hardware Export Pipeline (`MediaCodec` & `MediaMuxer`)
* **Hardware Accelerated Encoding**: H.264/AVC and H.265/HEVC hardware encoder integration via Android `MediaCodec`.
* **Custom Export Configurations**: 720p, 1080p, and 4K UHD resolutions at 24, 30, and 60 FPS with adaptive bitrate calculation.
* **Background Export Service**: Android Foreground Service with persistent notification and cancellation capabilities.
