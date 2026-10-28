package com.jevon.personalos

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.jevon.personalos.data.PersonalDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class MigrationTest {
    @Test fun versionOneRecordsSurviveWithSafeDefaults() = migrated(1)
    @Test fun versionTwoSubscriptionsAndPaymentLinksSurvive() = migrated(2)
    @Test fun versionThreeScheduleGainsSafeDefaultsWithoutDataLoss() = migrated(3)
    private fun migrated(version: Int) = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // Build the exported v1 schema and let current Room validate the entire migrated v2 schema.
        val schema = JSONObject(instrumentation.context.assets.open("com.jevon.personalos.data.PersonalDatabase/$version.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val name = "migration-1-2-${java.util.UUID.randomUUID()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                        val indices = entity.optJSONArray("indices")
                        for (j in 0 until (indices?.length() ?: 0)) db.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Only v1 is created here")
            }).build())
        helper.writableDatabase.apply {
            execSQL("INSERT INTO accounts (id,name,currencyCode,openingBalanceMinor,archived) VALUES ('bank','Bank','USD',-100,0)")
            execSQL("INSERT INTO accounts (id,name,currencyCode,openingBalanceMinor,archived) VALUES ('cash','Cash','USD',200,1)")
            execSQL("INSERT INTO categories (id,name) VALUES ('food','Food')")
            execSQL("INSERT INTO entries (id,kind,amountMinor,currencyCode,occurredOnEpochDay,fromAccountId,toAccountId,categoryId,note) VALUES ('transfer','TRANSFER',50,'USD',20000,'bank','cash',NULL,'Old transfer')")
            execSQL("INSERT INTO entries (id,kind,amountMinor,currencyCode,occurredOnEpochDay,fromAccountId,toAccountId,categoryId,note) VALUES ('expense','EXPENSE',10,'USD',20000,'bank',NULL,'food','Old expense')")
            execSQL("INSERT INTO budgets VALUES ('budget','Old plan','USD',500,19999,20030,NULL)")
            execSQL("INSERT INTO reminders (id,title,dueAtMillis,completedAtMillis,repeat_frequency,repeat_interval,repeat_anchorEpochDay,repeat_untilEpochDay) VALUES ('reminder','Old reminder',NULL,NULL,NULL,1,0,NULL)")
            execSQL("INSERT INTO subscriptions (id,name,amountMinor,currencyCode,nextDueEpochDay,active,repeat_frequency,repeat_interval,repeat_anchorEpochDay,repeat_untilEpochDay) VALUES ('plan','Old plan',10,'USD',20000,1,'MONTHLY',1,20000,NULL)")
            execSQL("INSERT INTO subscription_payments VALUES ('link','plan',20000,'expense')")
        }
        helper.close()
        val db = Room.databaseBuilder(context, PersonalDatabase::class.java, name).addMigrations(PersonalDatabase.MIGRATION_1_2, PersonalDatabase.MIGRATION_2_3, PersonalDatabase.MIGRATION_3_4).build()
        try {
            val dao = db.personalDao()
            val bank = dao.balances().first { it.account.id == "bank" }
            assertEquals(-100L, bank.account.openingBalanceMinor); assertEquals(-160L, bank.balance)
            assertEquals("OTHER", bank.account.type); assertEquals("", bank.account.notes)
            assertTrue(dao.balances().first { it.account.id == "cash" }.account.archived)
            assertNull(dao.categories().single().kind); assertFalse(dao.categories().single().archived)
            assertEquals(0, dao.findEntry("transfer")!!.minuteOfDay); assertEquals(50L, dao.findEntry("transfer")!!.amountMinor)
            assertEquals(1, dao.budgetProjections(20000).size)
            assertNull(dao.todayReminders(Long.MAX_VALUE).single().dueAtMillis)
            assertEquals("UTC",dao.reminder("reminder")!!.zoneId); assertFalse(dao.reminder("reminder")!!.dateOnly)
            assertTrue(dao.alertPreferences().isEmpty()); assertTrue(dao.completions("reminder").isEmpty())
            val plan = dao.subscription("plan")!!
            assertEquals("", plan.description); assertEquals("", plan.notes)
            assertNull(plan.accountId); assertNull(plan.categoryId)
            assertEquals("expense", dao.paymentsFor("plan").single().entryId)
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            val backup = com.jevon.personalos.backup.BackupCodec.encode(com.jevon.personalos.backup.BackupStore(db).snapshot(), "migrated-v1")
            val restored = Room.inMemoryDatabaseBuilder(context, PersonalDatabase::class.java).build()
            try {
                com.jevon.personalos.backup.BackupStore(restored).replace(com.jevon.personalos.backup.BackupCodec.decode(java.io.ByteArrayInputStream(backup)))
                assertEquals(dao.balances(), restored.personalDao().balances())
                assertEquals(dao.findEntry("transfer"), restored.personalDao().findEntry("transfer"))
                assertEquals(dao.budgetProjections(20000), restored.personalDao().budgetProjections(20000))
                assertEquals(dao.todayReminders(Long.MAX_VALUE), restored.personalDao().todayReminders(Long.MAX_VALUE))
                assertEquals(dao.subscriptions(), restored.personalDao().subscriptions())
                assertEquals(dao.paymentsFor("plan"), restored.personalDao().paymentsFor("plan"))
            } finally { restored.close() }
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
