package io.rebble.libpebblecommon.database.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import io.rebble.libpebblecommon.database.Database
import io.rebble.libpebblecommon.database.entity.LockerEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class SideloadOrderTest {
    private lateinit var db: Database
    private lateinit var dao: LockerEntryRealDao
    private val limit = 100

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder<Database>(InstrumentationRegistry.getInstrumentation().targetContext)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        dao = db.lockerEntryDao()
    }
    @After fun close() = db.close()

    private fun entry(title: String, index: Int, type: String = "watchapp") = LockerEntry(
        id = Uuid.random(), version = "1.0", title = title, type = type,
        developerName = "test", configurable = false, pbwVersionCode = "1",
        platforms = emptyList(), orderIndex = index,
    )

    @Test fun repeatedUpdatesPreserveFirstMiddleAndFaceOrder() = runBlocking {
        val entries = listOf(entry("A", 0), entry("B", 1), entry("C", 2))
        val faces = listOf(entry("Face A", 0, "watchface"), entry("Face B", 1, "watchface"))
        dao.insertOrReplaceAndOrder(entries + faces, limit)
        repeat(3) {
            dao.insertSideloadPreservingOrder(entries[0].copy(version = "2.0", orderIndex = 11), limit)
            dao.insertSideloadPreservingOrder(entries[1].copy(version = "2.0", orderIndex = 11), limit)
            dao.insertSideloadPreservingOrder(faces[1].copy(version = "2.0", orderIndex = -1), limit)
        }
        assertEquals(entries.map { it.id }, dao.getAppOrderFlow("watchapp", limit).first())
        assertEquals(faces.map { it.id }, dao.getAppOrderFlow("watchface", limit).first())
        assertEquals("2.0", dao.getEntry(entries[0].id)?.version)
    }

    @Test fun newAndDeletedEntriesUseTheSuppliedPosition() = runBlocking {
        val a = entry("A", 0)
        val b = entry("B", 1)
        dao.insertOrReplaceAndOrder(listOf(a, b), limit)
        val added = entry("C", 11)
        dao.insertSideloadPreservingOrder(added, limit)
        assertEquals(listOf(a.id, b.id, added.id), dao.getAppOrderFlow("watchapp", limit).first())
        dao.markForDeletion(a.id)
        dao.updateOrder("watchapp")
        dao.insertSideloadPreservingOrder(a.copy(orderIndex = 11), limit)
        assertEquals(listOf(b.id, added.id, a.id), dao.getAppOrderFlow("watchapp", limit).first())
    }

    @Test fun typeChangesUseTheNewTypesInsertionPosition() = runBlocking {
        val app = entry("App", 0)
        val face = entry("Face", 0, "watchface")
        dao.insertOrReplaceAndOrder(listOf(app, face), limit)
        dao.insertSideloadPreservingOrder(app.copy(type = "watchface", orderIndex = 11), limit)
        assertEquals(listOf(face.id, app.id), dao.getAppOrderFlow("watchface", limit).first())
    }
}
