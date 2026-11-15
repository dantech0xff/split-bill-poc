package com.dantech.splitvibe.data.bills

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A saved bill: the whole [SavedBill] as JSON in [document], and copies of what lists and searches need. */
@Entity(tableName = "bills", indices = [Index("updatedAt")])
data class BillEntity(
    @PrimaryKey val id: String,
    val revision: Int,
    val status: String,
    val updatedAt: Long,
    val merchant: String?,
    val date: String?,
    val currency: String,
    val fractionDigits: Int,
    val totalMinor: Long,
    /** The participants' names, for the list. */
    val people: String,
    /** Merchant, date, items and people without accents or case, which is what a search looks in. */
    val searchText: String,
    val document: String,
)

/** What the history shows of a bill without reading the whole document. */
data class BillListing(
    val id: String,
    val status: String,
    val updatedAt: Long,
    val merchant: String?,
    val date: String?,
    val currency: String,
    val fractionDigits: Int,
    val totalMinor: Long,
    val people: String,
)

/** Someone the user split a bill with, remembered by name only to suggest it again. */
@Entity(tableName = "people", indices = [Index(value = ["normalizedName"], unique = true)])
data class PersonEntity(@PrimaryKey val id: String, val name: String, val normalizedName: String, val lastUsedAt: Long)

@Dao
abstract class BillDao {
    /** Bills whose search text contains [query], newest first. [query] is already normalized and escaped for LIKE. */
    @Query(
        "SELECT id, status, updatedAt, merchant, date, currency, fractionDigits, totalMinor, people FROM bills " +
            "WHERE searchText LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY updatedAt DESC",
    )
    abstract fun listings(query: String): Flow<List<BillListing>>

    @Query("SELECT * FROM bills WHERE id = :id")
    abstract suspend fun get(id: String): BillEntity?

    @Query("SELECT revision FROM bills WHERE id = :id")
    protected abstract suspend fun revisionOf(id: String): Int?

    @Upsert
    protected abstract suspend fun upsert(bill: BillEntity)

    /** Writes [bill] only if the stored copy is still at [expectedRevision] (0: not stored yet). */
    @Transaction
    open suspend fun write(bill: BillEntity, expectedRevision: Int): Boolean {
        if ((revisionOf(bill.id) ?: 0) != expectedRevision) return false
        upsert(bill)
        return true
    }

    @Query("DELETE FROM bills WHERE id = :id")
    abstract suspend fun delete(id: String)
}

@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY lastUsedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE normalizedName = :normalizedName")
    suspend fun byName(normalizedName: String): PersonEntity?

    @Upsert
    suspend fun upsert(person: PersonEntity)

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [BillEntity::class, PersonEntity::class], version = 1, exportSchema = true)
abstract class SplitVibeDatabase : RoomDatabase() {
    abstract fun bills(): BillDao

    abstract fun people(): PersonDao

    companion object {
        fun create(context: Context): SplitVibeDatabase =
            Room.databaseBuilder(context.applicationContext, SplitVibeDatabase::class.java, "split-vibe.db").build()
    }
}
