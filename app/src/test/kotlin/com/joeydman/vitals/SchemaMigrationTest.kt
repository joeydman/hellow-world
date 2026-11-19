package com.joeydman.vitals

import com.joeydman.vitals.data.Migrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/**
 * Room exports the schema it expects during the KSP step (app/schemas). Comparing the
 * migration's SQL with it catches column or nullability mistakes without an emulator.
 */
class SchemaMigrationTest {

    private fun createSql(version: Int, table: String): String? {
        val json = File("schemas/com.joeydman.vitals.data.VitalsDatabase/$version.json").readText()
        val block = Regex("\"tableName\":\\s*\"$table\".*?\"createSql\":\\s*\"(.*?)\"", RegexOption.DOT_MATCHES_ALL)
            .find(json) ?: return null
        return block.groupValues[1].replace("\\u0060", "`").replace("\${TABLE_NAME}", table)
    }

    @Test fun chargeSessionMigrationMatchesRoomSchema() {
        val expected = createSql(2, "charge_session")
        assertNotNull("Room did not export charge_session in schema v2", expected)
        assertEquals(expected, Migrations.CREATE_CHARGE_SESSION)
    }
}
