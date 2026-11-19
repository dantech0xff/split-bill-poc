package com.dantech.splitvibe.data.bills

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.contentWords
import com.dantech.splitvibe.data.people.PeopleRepository
import com.dantech.splitvibe.data.recurring.RecurringBillEntity
import com.dantech.splitvibe.domain.BillCategory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The move of a phone's database from version 2 to version 3. The test builds a version 2 database
 * from the schema Room exported for it, with bills saved before and after bills had a category,
 * and opens it as the app does. Room migrates it on the way, and refuses to open it unless the
 * migrated tables are exactly what version 3 declares.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file: File = context.getDatabasePath(NAME)
    private val json = Json { encodeDefaults = true }

    @After
    fun tearDown() {
        context.deleteDatabase(NAME)
    }

    /** [saved] as version 2 stored it: the whole bill as JSON, with copies of what the list shows. */
    private fun SQLiteDatabase.insertBill(saved: SavedBill, document: String) {
        execSQL(
            "INSERT INTO bills (id, revision, status, updatedAt, merchant, date, currency, fractionDigits, totalMinor, people, " +
                "searchText, document) VALUES (?, 1, 'FINAL', 1, ?, ?, ?, ?, ?, 'You', '', ?)",
            arrayOf<Any?>(
                saved.id,
                saved.bill.merchant,
                saved.bill.date,
                saved.bill.currency.code,
                saved.bill.currency.fractionDigits,
                saved.bill.totalMinor,
                document,
            ),
        )
    }

    /** A database at version 2 with the tables, indices and identity Room exported for that version. */
    private fun versionTwo(fill: SQLiteDatabase.() -> Unit) {
        val schema = Json.parseToJsonElement(File(SCHEMAS, "2.json").readText()).jsonObject.getValue("database").jsonObject
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schema.getValue("entities").jsonArray.map { it.jsonObject }.forEach { entity ->
                val table = entity.getValue("tableName").jsonPrimitive.content
                db.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach { index ->
                    db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.fill()
            db.version = 2
        }
    }

    @Test
    fun `version 2 moves to 3 with the recurring bills, and each bill's category read from its document`() {
        val rent = SpendingFixtures.finished("rent", "2026-11-01", "Rent", BillCategory.RENT, 9_000_000, listOf("Minh"))
        val older = SpendingFixtures.finished("older", "2026-10-02", "Phở Thìn", BillCategory.FOOD_AND_DRINK, 120_000, listOf("Lan"))
        val unknown = SpendingFixtures.finished("unknown", "2026-10-03", "Ga Hà Nội", BillCategory.TRANSPORT, 80_000, emptyList())
        versionTwo {
            insertBill(rent, json.encodeToString(SavedBill.serializer(), rent))
            // Saved before bills had a category: the field is not in the document at all.
            insertBill(older, withCategory(json.encodeToString(SavedBill.serializer(), older), null))
            // Saved by a version that knows a category this one does not.
            insertBill(unknown, withCategory(json.encodeToString(SavedBill.serializer(), unknown), "pets"))
        }

        val database = Room.databaseBuilder(context, SplitVibeDatabase::class.java, NAME).allowMainThreadQueries().build()
        try {
            val listings = runBlocking { database.bills().listings("").first() }
            assertEquals(mapOf("rent" to "rent", "older" to "other", "unknown" to "other"), listings.associate { it.id to it.category })

            // The bills read as they were saved, and the new table takes recurring bills.
            val bills = BillRepository(database, PeopleRepository(database), BillPages(context), contentWords())
            assertEquals(rent, runBlocking { bills.get("rent") })
            assertEquals(BillCategory.OTHER, runBlocking { bills.get("older") }?.bill?.category)
            // A category this version does not know reads as other in the document too, as in the list.
            assertEquals(unknown.bill.copy(category = BillCategory.OTHER), runBlocking { bills.get("unknown") }?.bill)
            assertEquals(3, runBlocking { bills.finished().first() }.size)
            val recurring = RecurringBillEntity("r1", "Rent", "MONTHLY", "2026-12-01", false, 1, "2026-11-15", "{}")
            runBlocking { database.recurringBills().insert(recurring) }
            assertEquals(listOf(recurring), runBlocking { database.recurringBills().active() })
            assertEquals(3, database.openHelper.readableDatabase.version)
        } finally {
            database.close()
        }
    }

    /** [document] with its bill's category set to [category], or without one when it is null. */
    private fun withCategory(document: String, category: String?): String {
        val saved = Json.parseToJsonElement(document).jsonObject
        val bill = saved.getValue("bill").jsonObject
        val changed = if (category == null) bill - "category" else bill + ("category" to JsonPrimitive(category))
        return JsonObject(saved + ("bill" to JsonObject(changed))).toString()
    }

    private companion object {
        const val NAME = "migration-test.db"

        /** Where Room exports the schemas, relative to the module directory the tests run in. */
        const val SCHEMAS = "schemas/com.dantech.splitvibe.data.bills.SplitVibeDatabase"
    }
}
