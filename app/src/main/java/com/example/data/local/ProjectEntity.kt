package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.AspectRatio
import com.example.domain.model.ExportSettings
import com.example.domain.model.Project
import com.example.domain.model.Timeline

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val canvasRatio: String,
    val timelineJson: String,
    val exportSettingsJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDraft: Boolean,
    val thumbnailUri: String?
)
