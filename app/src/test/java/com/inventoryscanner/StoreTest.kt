package com.inventoryscanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val file get() = File(tmp.root, "area.jsonl")

    @Test fun roundTripAndRestart() {
        val store = Store(file)
        assertNull(store.load())
        val started = store.start("  Aisle 3 ")
        assertEquals("Aisle 3", started.name)
        store.add("0012345", "2026-09-19 10:00:00")
        val s = store.add("ABC")
        assertTrue(s.time, Regex("""\d{4}-\d\d-\d\d \d\d:\d\d:\d\d""").matches(s.time))

        val loaded = Store(file).load()!! // new instance = simulated restart
        assertEquals(started.batchId, loaded.batchId)
        assertEquals("Aisle 3", loaded.name)
        assertEquals(listOf(Scan("2026-09-19 10:00:00", "0012345"), s), loaded.scans)
    }

    @Test fun deleteRemovesOnlyThatIndex() {
        val store = Store(file)
        store.start("A")
        listOf("a", "b", "c").forEach { store.add(it, "t") }
        store.delete(1)
        assertEquals(listOf("a", "c"), store.load()!!.scans.map { it.code })
        store.add("d", "t")
        assertEquals(listOf("a", "c", "d"), Store(file).load()!!.scans.map { it.code })
    }

    @Test fun renameKeepsBatchAndScans() {
        val store = Store(file)
        val started = store.start("A")
        store.add("a", "t"); store.add("b", "t")
        assertThrows(IllegalArgumentException::class.java) { store.rename("  ") }
        store.rename("  B ")
        store.add("c", "t")
        val loaded = Store(file).load()!!
        assertEquals("B", loaded.name)
        assertEquals(started.batchId, loaded.batchId)
        assertEquals(listOf("a", "b", "c"), loaded.scans.map { it.code })
    }

    @Test fun clearLeavesNothing() {
        val store = Store(file)
        store.start("A")
        store.add("x")
        store.clear()
        assertNull(store.load())
        store.clear() // idempotent
        assertEquals(emptyList<Scan>(), store.start("B").scans)
    }

    @Test fun oddCodesRoundTrip() {
        val codes = listOf("line1\nline2\r\n", "say \"hi\"\t\\", "čćžšđ 日本 🙂", "=1+1", "")
        val store = Store(file)
        store.start("A")
        codes.forEach { store.add(it, "t") }
        assertEquals(codes, Store(file).load()!!.scans.map { it.code })
    }

    @Test fun truncatedTrailingLineIsSkipped() {
        val store = Store(file)
        store.start("A")
        store.add("good", "t")
        file.appendText("\n[\"2026-09-19 10:00:00\",\"trunc") // crash mid-append
        assertEquals(listOf("good"), store.load()!!.scans.map { it.code })
        store.add("after", "t") // the next append must not be glued to the broken line
        assertEquals(listOf("good", "after"), store.load()!!.scans.map { it.code })
    }

    @Test fun rowsInContractOrderTrimmed() {
        val area = Area(" Aisle 3 ", "id", listOf(Scan("t1", " 007 "), Scan("t2", "x")))
        assertEquals(
            listOf(listOf("t1", " 007 ", "Aisle 3", "Ana"), listOf("t2", "x", "Aisle 3", "Ana")),
            area.rows("  Ana "),
        )
    }
}
