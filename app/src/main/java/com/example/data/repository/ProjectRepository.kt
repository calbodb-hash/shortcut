package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.ProjectConverters
import com.example.data.local.ProjectEntity
import com.example.domain.model.AspectRatio
import com.example.domain.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface IProjectRepository {
    fun getAllProjects(): Flow<List<Project>>
    fun getDraftProjects(): Flow<List<Project>>
    fun observeProject(id: String): Flow<Project?>
    suspend fun getProject(id: String): Project?
    suspend fun saveProject(project: Project)
    suspend fun deleteProject(id: String)
}

class ProjectRepository(
    private val database: AppDatabase,
    private val converters: ProjectConverters = ProjectConverters()
) : IProjectRepository {

    private val dao = database.projectDao()

    override fun getAllProjects(): Flow<List<Project>> {
        return dao.getAllProjects().map { entities ->
            entities.map { it.toDomain(converters) }
        }
    }

    override fun getDraftProjects(): Flow<List<Project>> {
        return dao.getDraftProjects().map { entities ->
            entities.map { it.toDomain(converters) }
        }
    }

    override fun observeProject(id: String): Flow<Project?> {
        return dao.observeProjectById(id).map { entity ->
            entity?.toDomain(converters)
        }
    }

    override suspend fun getProject(id: String): Project? = withContext(Dispatchers.IO) {
        dao.getProjectById(id)?.toDomain(converters)
    }

    override suspend fun saveProject(project: Project) = withContext(Dispatchers.IO) {
        val entity = project.toEntity(converters)
        dao.insertOrUpdateProject(entity)
    }

    override suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        dao.deleteProjectById(id)
    }
}

private fun ProjectEntity.toDomain(converters: ProjectConverters): Project {
    val ratio = try {
        AspectRatio.valueOf(canvasRatio)
    } catch (e: Exception) {
        AspectRatio.RATIO_9_16
    }
    return Project(
        id = id,
        title = title,
        canvasRatio = ratio,
        timeline = converters.jsonToTimeline(timelineJson),
        exportSettings = converters.jsonToExportSettings(exportSettingsJson),
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDraft = isDraft,
        thumbnailUri = thumbnailUri
    )
}

private fun Project.toEntity(converters: ProjectConverters): ProjectEntity {
    return ProjectEntity(
        id = id,
        title = title,
        canvasRatio = canvasRatio.name,
        timelineJson = converters.timelineToJson(timeline),
        exportSettingsJson = converters.exportSettingsToJson(exportSettings),
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis(),
        isDraft = isDraft,
        thumbnailUri = thumbnailUri
    )
}
