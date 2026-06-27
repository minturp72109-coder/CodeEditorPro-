package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY lastModified DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: Int): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    // Git Branches
    @Query("SELECT * FROM git_branches WHERE projectId = :projectId")
    fun getBranchesForProject(projectId: Int): Flow<List<GitBranchEntity>>

    @Query("SELECT * FROM git_branches WHERE projectId = :projectId AND isCurrent = 1 LIMIT 1")
    suspend fun getCurrentBranch(projectId: Int): GitBranchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBranch(branch: GitBranchEntity): Long

    @Update
    suspend fun updateBranch(branch: GitBranchEntity)

    @Query("UPDATE git_branches SET isCurrent = 0 WHERE projectId = :projectId")
    suspend fun clearCurrentBranches(projectId: Int)

    // Git Commits
    @Query("SELECT * FROM git_commits WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getCommitsForProject(projectId: Int): Flow<List<GitCommitEntity>>

    @Query("SELECT * FROM git_commits WHERE projectId = :projectId AND branchName = :branchName ORDER BY timestamp DESC")
    fun getCommitsForBranch(projectId: Int, branchName: String): Flow<List<GitCommitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommit(commit: GitCommitEntity): Long

    // Recent Files
    @Query("SELECT * FROM recent_files WHERE projectId = :projectId ORDER BY lastOpened DESC LIMIT 20")
    fun getRecentFiles(projectId: Int): Flow<List<RecentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentFile(recentFile: RecentFileEntity)

    @Query("DELETE FROM recent_files WHERE projectId = :projectId AND filePath = :filePath")
    suspend fun deleteRecentFile(projectId: Int, filePath: String)
}
