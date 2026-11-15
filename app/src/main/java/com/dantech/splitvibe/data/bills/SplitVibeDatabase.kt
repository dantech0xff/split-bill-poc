package com.dantech.splitvibe.data.bills

import android.content.Context
import androidx.room.AutoMigration
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

/**
 * A way the user takes money, as the JSON of a [com.dantech.splitvibe.domain.payments.PaymentMethod].
 * It never leaves the phone, except as the one copy a payment request carries.
 */
@Entity(tableName = "payment_methods")
data class PaymentMethodEntity(
    @PrimaryKey val id: String,
    val document: String,
    val addedAt: Long,
    /** The method requests use unless the user picks another one; one at most. */
    val isMain: Boolean,
)

@Dao
abstract class PaymentMethodDao {
    @Query("SELECT * FROM payment_methods ORDER BY isMain DESC, addedAt")
    abstract fun all(): Flow<List<PaymentMethodEntity>>

    @Query("SELECT * FROM payment_methods WHERE id = :id")
    abstract suspend fun get(id: String): PaymentMethodEntity?

    @Upsert
    abstract suspend fun upsert(method: PaymentMethodEntity)

    @Query("DELETE FROM payment_methods WHERE id = :id")
    protected abstract suspend fun deleteRow(id: String)

    @Query("UPDATE payment_methods SET isMain = (id = :id)")
    protected abstract suspend fun setMain(id: String)

    @Query("SELECT id FROM payment_methods ORDER BY isMain DESC, addedAt LIMIT 1")
    protected abstract suspend fun first(): String?

    @Query("UPDATE payment_methods SET isMain = 1 WHERE id = :id AND NOT EXISTS (SELECT 1 FROM payment_methods WHERE isMain = 1)")
    abstract suspend fun makeMainIfNone(id: String)

    /** Makes [id] the main method, and every other one not. */
    @Transaction
    open suspend fun makeMain(id: String) = setMain(id)

    /** Deletes [id]; when it was the main method, the oldest one left becomes the main one. */
    @Transaction
    open suspend fun delete(id: String) {
        deleteRow(id)
        first()?.let { setMain(it) }
    }
}

/**
 * The last answer of the proxy for one [key], such as the list of groups, so that groups and
 * requests can be shown without a connection. It only ever holds what the account signed in now
 * may read, and is emptied on every sign-in, sign-out and account deletion.
 */
@Entity(tableName = "proxy_cache")
data class ProxyCacheEntity(@PrimaryKey val key: String, val document: String, val savedAt: Long)

@Dao
interface ProxyCacheDao {
    @Query("SELECT * FROM proxy_cache WHERE `key` = :key")
    fun watch(key: String): Flow<ProxyCacheEntity?>

    @Query("SELECT * FROM proxy_cache WHERE `key` = :key")
    suspend fun get(key: String): ProxyCacheEntity?

    @Upsert
    suspend fun put(entry: ProxyCacheEntity)

    @Query("DELETE FROM proxy_cache WHERE `key` = :key")
    suspend fun remove(key: String)

    @Query("DELETE FROM proxy_cache")
    suspend fun clear()
}

@Database(
    entities = [BillEntity::class, PersonEntity::class, PaymentMethodEntity::class, ProxyCacheEntity::class],
    version = 2,
    exportSchema = true,
    // Version 2 only adds tables, which Room migrates on its own.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class SplitVibeDatabase : RoomDatabase() {
    abstract fun bills(): BillDao

    abstract fun people(): PersonDao

    abstract fun paymentMethods(): PaymentMethodDao

    abstract fun proxyCache(): ProxyCacheDao

    companion object {
        fun create(context: Context): SplitVibeDatabase =
            Room.databaseBuilder(context.applicationContext, SplitVibeDatabase::class.java, "split-vibe.db").build()
    }
}
