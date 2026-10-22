package com.bentusk.app.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/** One photo in a plan. [thumbPath] is a private copy so the plan survives the original being moved. */
@Entity(
    tableName = "plan_items",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("planId")],
)
data class PlanItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val sourceUri: String,
    val thumbPath: String,
    val position: Int,
)

@Dao
abstract class PlannerDao {
    @Query("SELECT * FROM plans ORDER BY createdAt")
    abstract fun plans(): Flow<List<PlanEntity>>

    @Insert
    abstract suspend fun insertPlan(plan: PlanEntity): Long

    @Query("UPDATE plans SET name = :name WHERE id = :id")
    abstract suspend fun renamePlan(id: Long, name: String)

    @Query("DELETE FROM plans WHERE id = :id")
    abstract suspend fun deletePlan(id: Long)

    @Query("SELECT * FROM plan_items WHERE planId = :planId ORDER BY position")
    abstract fun items(planId: Long): Flow<List<PlanItemEntity>>

    @Query("SELECT * FROM plan_items WHERE planId = :planId ORDER BY position")
    abstract suspend fun itemsNow(planId: Long): List<PlanItemEntity>

    @Insert
    abstract suspend fun insertItems(items: List<PlanItemEntity>)

    @Query("DELETE FROM plan_items WHERE id = :id")
    abstract suspend fun deleteItem(id: Long)

    @Query("UPDATE plan_items SET position = :position WHERE id = :id")
    abstract suspend fun setPosition(id: Long, position: Int)

    @Transaction
    open suspend fun reorder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id -> setPosition(id, index) }
    }
}

@Database(entities = [PlanEntity::class, PlanItemEntity::class], version = 1, exportSchema = true)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun dao(): PlannerDao
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): PlannerDatabase =
        Room.databaseBuilder(context, PlannerDatabase::class.java, "planner.db").build()

    @Provides
    fun plannerDao(db: PlannerDatabase): PlannerDao = db.dao()
}
