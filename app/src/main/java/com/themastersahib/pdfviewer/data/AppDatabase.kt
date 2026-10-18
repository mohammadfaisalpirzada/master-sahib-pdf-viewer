package com.themastersahib.pdfviewer.data

import android.content.Context
import androidx.room.*

@Entity
data class RecentFile(
    @PrimaryKey val uri: String,
    val name: String,
    val lastOpened: Long,
    val lastPage: Int = 0,
    val pageCount: Int = 0
)

@Entity
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val name: String,
    val page: Int,
    val createdAt: Long
)

@Dao
interface RecentDao {
    @Query("SELECT * FROM RecentFile ORDER BY lastOpened DESC LIMIT 100")
    suspend fun all(): List<RecentFile>

    @Upsert
    suspend fun upsert(file: RecentFile)

    @Query("UPDATE RecentFile SET lastPage = :page WHERE uri = :uri")
    suspend fun updatePage(uri: String, page: Int)

    @Delete
    suspend fun delete(file: RecentFile)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM Bookmark ORDER BY createdAt DESC")
    suspend fun all(): List<Bookmark>

    @Insert
    suspend fun add(bookmark: Bookmark)

    @Delete
    suspend fun delete(bookmark: Bookmark)
}

@Database(entities = [RecentFile::class, Bookmark::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentDao(): RecentDao
    abstract fun bookmarkDao(): BookmarkDao
}

/** Simple process-wide singleton. */
object Db {
    @Volatile private var instance: AppDatabase? = null
    fun get(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "master_sahib_pdf.db"
            ).build().also { instance = it }
        }
}
