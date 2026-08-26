package com.example.data.local

import androidx.room.TypeConverter
import com.example.domain.model.*
import org.json.JSONArray
import org.json.JSONObject

class ProjectConverters {

    @TypeConverter
    fun timelineToJson(timeline: Timeline): String {
        val root = JSONObject()
        
        // Video Clips
        val videoArray = JSONArray()
        for (clip in timeline.videoClips) {
            val obj = JSONObject().apply {
                put("id", clip.id)
                put("sourceUri", clip.sourceUri)
                put("mediaType", clip.mediaType.name)
                put("name", clip.name)
                put("sourceDurationMs", clip.sourceDurationMs)
                put("sourceStartTimeMs", clip.sourceStartTimeMs)
                put("sourceEndTimeMs", clip.sourceEndTimeMs)
                put("timelineStartMs", clip.timelineStartMs)
                put("speed", clip.speed.toDouble())
                put("volume", clip.volume.toDouble())
                put("isMuted", clip.isMuted)
                put("filter", clip.filter.name)
                put("effect", clip.effect.name)
                put("thumbnailColorSeed", clip.thumbnailColorSeed)
                
                // Transform
                val transformObj = JSONObject().apply {
                    put("scale", clip.transform.scale.toDouble())
                    put("translationX", clip.transform.translationX.toDouble())
                    put("translationY", clip.transform.translationY.toDouble())
                    put("rotation", clip.transform.rotation.toDouble())
                    put("isFlippedHorizontal", clip.transform.isFlippedHorizontal)
                    put("isFlippedVertical", clip.transform.isFlippedVertical)
                    put("framingMode", clip.transform.framingMode.name)
                    
                    // Crop
                    val cropObj = JSONObject().apply {
                        put("isEnabled", clip.transform.crop.isEnabled)
                        put("left", clip.transform.crop.left.toDouble())
                        put("top", clip.transform.crop.top.toDouble())
                        put("right", clip.transform.crop.right.toDouble())
                        put("bottom", clip.transform.crop.bottom.toDouble())
                        clip.transform.crop.targetRatio?.let { put("targetRatio", it.name) }
                    }
                    put("crop", cropObj)
                }
                put("transform", transformObj)

                // Adjustments
                val adjustObj = JSONObject().apply {
                    put("brightness", clip.adjustments.brightness.toDouble())
                    put("contrast", clip.adjustments.contrast.toDouble())
                    put("saturation", clip.adjustments.saturation.toDouble())
                    put("exposure", clip.adjustments.exposure.toDouble())
                    put("temperature", clip.adjustments.temperature.toDouble())
                    put("tint", clip.adjustments.tint.toDouble())
                    put("opacity", clip.adjustments.opacity.toDouble())
                }
                put("adjustments", adjustObj)
            }
            videoArray.put(obj)
        }
        root.put("videoClips", videoArray)

        // Audio Clips
        val audioArray = JSONArray()
        for (audio in timeline.audioClips) {
            val obj = JSONObject().apply {
                put("id", audio.id)
                put("sourceUri", audio.sourceUri)
                put("title", audio.title)
                put("audioTrackType", audio.audioTrackType.name)
                put("sourceDurationMs", audio.sourceDurationMs)
                put("sourceStartTimeMs", audio.sourceStartTimeMs)
                put("sourceEndTimeMs", audio.sourceEndTimeMs)
                put("timelineStartMs", audio.timelineStartMs)
                put("timelineDurationMs", audio.timelineDurationMs)
                put("volume", audio.volume.toDouble())
                put("isMuted", audio.isMuted)
                put("fadeInMs", audio.fadeInMs)
                put("fadeOutMs", audio.fadeOutMs)
                put("speed", audio.speed.toDouble())
                put("pan", audio.pan.toDouble())
                put("duckingEnabled", audio.duckingEnabled)
                put("duckingLevel", audio.duckingLevel.toDouble())
                put("trackId", audio.trackId)
            }
            audioArray.put(obj)
        }
        root.put("audioClips", audioArray)

        // Text Layers
        val textArray = JSONArray()
        for (text in timeline.textLayers) {
            val obj = JSONObject().apply {
                put("id", text.id)
                put("text", text.text)
                put("timelineStartMs", text.timelineStartMs)
                put("timelineDurationMs", text.timelineDurationMs)
                put("positionX", text.positionX.toDouble())
                put("positionY", text.positionY.toDouble())
                put("scale", text.scale.toDouble())
                put("rotation", text.rotation.toDouble())
                put("fontSizeSp", text.fontSizeSp.toDouble())
                put("textColorArgb", text.textColorArgb)
                put("backgroundColorArgb", text.backgroundColorArgb)
                put("hasBackground", text.hasBackground)
                put("isBold", text.isBold)
                put("alignment", text.alignment)
                text.linkedToObjectId?.let { put("linkedToObjectId", it) }
            }
            textArray.put(obj)
        }
        root.put("textLayers", textArray)

        return root.toString()
    }

    @TypeConverter
    fun jsonToTimeline(jsonStr: String): Timeline {
        if (jsonStr.isBlank()) return Timeline()
        return try {
            val root = JSONObject(jsonStr)
            
            val videoList = mutableListOf<VideoClip>()
            val videoArray = root.optJSONArray("videoClips")
            if (videoArray != null) {
                for (i in 0 until videoArray.length()) {
                    val obj = videoArray.getJSONObject(i)
                    val transformObj = obj.optJSONObject("transform")
                    val transform = if (transformObj != null) {
                        val framingMode = try {
                            FramingMode.valueOf(transformObj.optString("framingMode", FramingMode.FIT.name))
                        } catch (e: Exception) {
                            FramingMode.FIT
                        }

                        val cropObj = transformObj.optJSONObject("crop")
                        val crop = if (cropObj != null) {
                            val targetRatio = try {
                                val ratioName = cropObj.optString("targetRatio", "")
                                if (ratioName.isNotEmpty()) AspectRatio.valueOf(ratioName) else null
                            } catch (e: Exception) {
                                null
                            }
                            CropState(
                                isEnabled = cropObj.optBoolean("isEnabled", false),
                                left = cropObj.optDouble("left", 0.0).toFloat(),
                                top = cropObj.optDouble("top", 0.0).toFloat(),
                                right = cropObj.optDouble("right", 1.0).toFloat(),
                                bottom = cropObj.optDouble("bottom", 1.0).toFloat(),
                                targetRatio = targetRatio
                            )
                        } else {
                            // Backwards compatibility with cropLeft/Right/Top/Bottom if present
                            val cLeft = transformObj.optDouble("cropLeft", 0.0).toFloat()
                            val cTop = transformObj.optDouble("cropTop", 0.0).toFloat()
                            val cRight = transformObj.optDouble("cropRight", 0.0).toFloat()
                            val cBottom = transformObj.optDouble("cropBottom", 0.0).toFloat()
                            if (cLeft != 0f || cTop != 0f || cRight != 0f || cBottom != 0f) {
                                CropState(
                                    isEnabled = true,
                                    left = cLeft,
                                    top = cTop,
                                    right = if (cRight > 0f) cRight else 1f,
                                    bottom = if (cBottom > 0f) cBottom else 1f
                                )
                            } else CropState()
                        }

                        Transform(
                            scale = transformObj.optDouble("scale", 1.0).toFloat(),
                            translationX = transformObj.optDouble("translationX", 0.0).toFloat(),
                            translationY = transformObj.optDouble("translationY", 0.0).toFloat(),
                            rotation = transformObj.optDouble("rotation", 0.0).toFloat(),
                            isFlippedHorizontal = transformObj.optBoolean("isFlippedHorizontal", false),
                            isFlippedVertical = transformObj.optBoolean("isFlippedVertical", false),
                            framingMode = framingMode,
                            crop = crop
                        )
                    } else Transform()

                    val adjustObj = obj.optJSONObject("adjustments")
                    val adjustments = if (adjustObj != null) {
                        ColorAdjustment(
                            brightness = adjustObj.optDouble("brightness", 0.0).toFloat(),
                            contrast = adjustObj.optDouble("contrast", 0.0).toFloat(),
                            saturation = adjustObj.optDouble("saturation", 0.0).toFloat(),
                            exposure = adjustObj.optDouble("exposure", 0.0).toFloat(),
                            temperature = adjustObj.optDouble("temperature", 0.0).toFloat(),
                            tint = adjustObj.optDouble("tint", 0.0).toFloat(),
                            opacity = adjustObj.optDouble("opacity", 1.0).toFloat()
                        )
                    } else ColorAdjustment()

                    val filter = try {
                        FilterPreset.valueOf(obj.optString("filter", FilterPreset.ORIGINAL.name))
                    } catch (e: Exception) {
                        FilterPreset.ORIGINAL
                    }

                    val effect = try {
                        EffectPreset.valueOf(obj.optString("effect", EffectPreset.NONE.name))
                    } catch (e: Exception) {
                        EffectPreset.NONE
                    }

                    val mediaType = try {
                        MediaType.valueOf(obj.optString("mediaType", MediaType.VIDEO.name))
                    } catch (e: Exception) {
                        MediaType.VIDEO
                    }

                    videoList.add(
                        VideoClip(
                            id = obj.getString("id"),
                            sourceUri = obj.getString("sourceUri"),
                            mediaType = mediaType,
                            name = obj.optString("name", "Clip"),
                            sourceDurationMs = obj.optLong("sourceDurationMs", 5000L),
                            sourceStartTimeMs = obj.optLong("sourceStartTimeMs", 0L),
                            sourceEndTimeMs = obj.optLong("sourceEndTimeMs", 5000L),
                            timelineStartMs = obj.optLong("timelineStartMs", 0L),
                            speed = obj.optDouble("speed", 1.0).toFloat(),
                            volume = obj.optDouble("volume", 1.0).toFloat(),
                            isMuted = obj.optBoolean("isMuted", false),
                            transform = transform,
                            adjustments = adjustments,
                            filter = filter,
                            effect = effect,
                            thumbnailColorSeed = obj.optLong("thumbnailColorSeed", 0xFF2A2E44)
                        )
                    )
                }
            }

            val audioList = mutableListOf<AudioClip>()
            val audioArray = root.optJSONArray("audioClips")
            if (audioArray != null) {
                for (i in 0 until audioArray.length()) {
                    val obj = audioArray.getJSONObject(i)
                    val audioTrackType = try {
                        AudioTrackType.valueOf(obj.optString("audioTrackType", AudioTrackType.MUSIC.name))
                    } catch (e: Exception) {
                        AudioTrackType.MUSIC
                    }
                    val srcDur = obj.optLong("sourceDurationMs", 10000L)
                    val srcStart = obj.optLong("sourceStartTimeMs", 0L)
                    val srcEnd = obj.optLong("sourceEndTimeMs", srcDur)
                    audioList.add(
                        AudioClip(
                            id = obj.getString("id"),
                            sourceUri = obj.getString("sourceUri"),
                            title = obj.optString("title", "Audio"),
                            audioTrackType = audioTrackType,
                            sourceDurationMs = srcDur,
                            sourceStartTimeMs = srcStart,
                            sourceEndTimeMs = srcEnd,
                            timelineStartMs = obj.optLong("timelineStartMs", 0L),
                            timelineDurationMs = obj.optLong("timelineDurationMs", srcDur),
                            volume = obj.optDouble("volume", 1.0).toFloat(),
                            isMuted = obj.optBoolean("isMuted", false),
                            fadeInMs = obj.optLong("fadeInMs", 0L),
                            fadeOutMs = obj.optLong("fadeOutMs", 0L),
                            speed = obj.optDouble("speed", 1.0).toFloat(),
                            pan = obj.optDouble("pan", 0.0).toFloat(),
                            duckingEnabled = obj.optBoolean("duckingEnabled", false),
                            duckingLevel = obj.optDouble("duckingLevel", 0.3).toFloat(),
                            trackId = obj.optString("trackId", "track_audio_music")
                        )
                    )
                }
            }

            val textList = mutableListOf<TextLayer>()
            val textArray = root.optJSONArray("textLayers")
            if (textArray != null) {
                for (i in 0 until textArray.length()) {
                    val obj = textArray.getJSONObject(i)
                    textList.add(
                        TextLayer(
                            id = obj.getString("id"),
                            text = obj.getString("text"),
                            timelineStartMs = obj.optLong("timelineStartMs", 0L),
                            timelineDurationMs = obj.optLong("timelineDurationMs", 3000L),
                            positionX = obj.optDouble("positionX", 0.0).toFloat(),
                            positionY = obj.optDouble("positionY", 0.0).toFloat(),
                            scale = obj.optDouble("scale", 1.0).toFloat(),
                            rotation = obj.optDouble("rotation", 0.0).toFloat(),
                            fontSizeSp = obj.optDouble("fontSizeSp", 22.0).toFloat(),
                            textColorArgb = obj.optLong("textColorArgb", 0xFFFFFFFF),
                            backgroundColorArgb = obj.optLong("backgroundColorArgb", 0x88000000),
                            hasBackground = obj.optBoolean("hasBackground", false),
                            isBold = obj.optBoolean("isBold", true),
                            alignment = obj.optInt("alignment", 1),
                            linkedToObjectId = if (obj.has("linkedToObjectId") && !obj.isNull("linkedToObjectId")) obj.optString("linkedToObjectId", null) else null
                        )
                    )
                }
            }

            Timeline(videoClips = videoList, audioClips = audioList, textLayers = textList)
        } catch (e: Exception) {
            Timeline()
        }
    }

    @TypeConverter
    fun exportSettingsToJson(settings: ExportSettings): String {
        return JSONObject().apply {
            put("resolution", settings.resolution.name)
            put("frameRate", settings.frameRate.name)
            put("codec", settings.codec.name)
            put("audioBitrateKbps", settings.audioBitrateKbps)
            put("useHardwareAcceleration", settings.useHardwareAcceleration)
        }.toString()
    }

    @TypeConverter
    fun jsonToExportSettings(jsonStr: String): ExportSettings {
        if (jsonStr.isBlank()) return ExportSettings()
        return try {
            val obj = JSONObject(jsonStr)
            ExportSettings(
                resolution = ExportResolution.valueOf(obj.optString("resolution", ExportResolution.RES_1080P.name)),
                frameRate = ExportFrameRate.valueOf(obj.optString("frameRate", ExportFrameRate.FPS_30.name)),
                codec = ExportCodec.valueOf(obj.optString("codec", ExportCodec.H264.name)),
                audioBitrateKbps = obj.optInt("audioBitrateKbps", 192),
                useHardwareAcceleration = obj.optBoolean("useHardwareAcceleration", true)
            )
        } catch (e: Exception) {
            ExportSettings()
        }
    }
}
