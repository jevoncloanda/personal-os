package com.jevon.personalos

import android.app.Application
import androidx.room.Room
import com.jevon.personalos.data.PersonalDatabase
import com.jevon.personalos.data.SettingsStore

class PersonalOsApplication : Application() {
    val database by lazy {
        Room.databaseBuilder(this, PersonalDatabase::class.java, "personal-os.db").addMigrations(PersonalDatabase.MIGRATION_1_2, PersonalDatabase.MIGRATION_2_3, PersonalDatabase.MIGRATION_3_4).build()
    }
    val settings by lazy { SettingsStore(this) }
}
