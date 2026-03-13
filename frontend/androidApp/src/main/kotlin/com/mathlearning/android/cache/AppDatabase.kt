package com.mathlearning.android.cache

import android.content.Context
import androidx.room.*

@Entity(tableName = "cached_knowledge_nodes")
data class CachedKnowledgeNode(
    @PrimaryKey val code: String,
    val nameEn: String,
    val nameZh: String,
    val parentCode: String?,
    val gradeStart: Int,
    val sortOrder: Int = 0,
)

@Entity(tableName = "cached_records")
data class CachedRecord(
    @PrimaryKey val id: String,
    val studentId: String,
    val questionText: String,
    val parentGuide: String?,
    val childScript: String?,
    val barModelJson: String?,
    @TypeConverters(Converters::class)
    val knowledgeTags: List<String>?,
    val rating: Int?,
    val createdAt: String,
)

@Entity(tableName = "cached_achievements", primaryKeys = ["code", "studentId"])
data class CachedAchievement(
    val code: String,
    val studentId: String,
    val title: String,
    val description: String,
    val icon: String,
    val unlocked: Boolean,
    val currentValue: Int,
    val targetValue: Int,
)

@Dao
interface KnowledgeNodeDao {
    @Query("SELECT * FROM cached_knowledge_nodes ORDER BY sortOrder")
    suspend fun getAll(): List<CachedKnowledgeNode>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<CachedKnowledgeNode>)

    @Query("DELETE FROM cached_knowledge_nodes")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(nodes: List<CachedKnowledgeNode>) {
        deleteAll()
        insertAll(nodes)
    }
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM cached_records WHERE studentId = :studentId ORDER BY createdAt DESC LIMIT 50")
    suspend fun getByStudent(studentId: String): List<CachedRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<CachedRecord>)

    @Query("DELETE FROM cached_records WHERE studentId = :studentId")
    suspend fun deleteByStudent(studentId: String)
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM cached_achievements WHERE studentId = :studentId")
    suspend fun getByStudent(studentId: String): List<CachedAchievement>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(achievements: List<CachedAchievement>)
}

@Database(
    entities = [CachedKnowledgeNode::class, CachedRecord::class, CachedAchievement::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun knowledgeNodeDao(): KnowledgeNodeDao
    abstract fun recordDao(): RecordDao
    abstract fun achievementDao(): AchievementDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "math_learning_cache")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
