package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val rootPath: String,
    val isFavorite: Boolean = false,
    val lastModified: Long = System.currentTimeMillis()
)

@Entity(tableName = "git_branches")
data class GitBranchEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: Int,
    val name: String,
    val isCurrent: Boolean = false
)

@Entity(tableName = "git_commits")
data class GitCommitEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: Int,
    val branchName: String,
    val message: String,
    val author: String = "Developer",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: Int,
    val filePath: String, // relative to project root
    val lastOpened: Long = System.currentTimeMillis()
)
