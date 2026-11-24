package com.example.bluelog.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class BlueLogDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "bluelog.db", null, 2) {

    private val _pocketsFlow = MutableStateFlow<List<Pocket>>(emptyList())
    private val _recordsFlow = MutableStateFlow<List<MoneyRecord>>(emptyList())

    init {
        refreshFlows()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE pockets (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                balancePaise INTEGER NOT NULL
            )
        """)
        db.execSQL("""
            CREATE TABLE records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                detailNote TEXT NOT NULL,
                amountPaise INTEGER NOT NULL,
                settledPaise INTEGER NOT NULL,
                type TEXT NOT NULL,
                pocketId INTEGER NOT NULL,
                toPocketId INTEGER,
                personName TEXT,
                createdAt INTEGER NOT NULL,
                dueDate INTEGER,
                isSettled INTEGER NOT NULL,
                affectsBalance INTEGER NOT NULL DEFAULT 1
            )
        """)

        val defaults = listOf(
            "Purse" to PocketType.CASH,
            "Hidden Locker" to PocketType.CASH,
            "SBI Bank" to PocketType.ONLINE,
            "GPay / UPI" to PocketType.ONLINE
        )
        for ((name, type) in defaults) {
            val cv = ContentValues().apply {
                put("name", name)
                put("type", type.name)
                put("balancePaise", 0L)
            }
            db.insert("pockets", null, cv)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE records ADD COLUMN affectsBalance INTEGER NOT NULL DEFAULT 1")
        }
    }

    private fun refreshFlows() {
        val db = readableDatabase

        val pocketList = mutableListOf<Pocket>()
        db.rawQuery("SELECT * FROM pockets ORDER BY id ASC", null).use { c ->
            while (c.moveToNext()) {
                pocketList.add(
                    Pocket(
                        id = c.getInt(0),
                        name = c.getString(1),
                        type = PocketType.valueOf(c.getString(2)),
                        balancePaise = c.getLong(3)
                    )
                )
            }
        }
        _pocketsFlow.value = pocketList

        val recordList = mutableListOf<MoneyRecord>()
        db.rawQuery("SELECT * FROM records ORDER BY createdAt DESC", null).use { c ->
            while (c.moveToNext()) {
                recordList.add(
                    MoneyRecord(
                        id = c.getInt(0),
                        title = c.getString(1),
                        detailNote = c.getString(2),
                        amountPaise = c.getLong(3),
                        settledPaise = c.getLong(4),
                        type = RecordType.valueOf(c.getString(5)),
                        pocketId = c.getInt(6),
                        toPocketId = if (c.isNull(7)) null else c.getInt(7),
                        personName = if (c.isNull(8)) null else c.getString(8),
                        createdAt = c.getLong(9),
                        dueDate = if (c.isNull(10)) null else c.getLong(10),
                        isSettled = c.getInt(11) == 1,
                        affectsBalance = if (c.columnCount > 12) c.getInt(12) == 1 else true
                    )
                )
            }
        }
        _recordsFlow.value = recordList
    }

    fun getAllPockets(): Flow<List<Pocket>> = _pocketsFlow.asStateFlow()
    fun getAllRecords(): Flow<List<MoneyRecord>> = _recordsFlow.asStateFlow()

    suspend fun insertPocket(pocket: Pocket) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("name", pocket.name)
            put("type", pocket.type.name)
            put("balancePaise", pocket.balancePaise)
        }
        writableDatabase.insert("pockets", null, cv)
        refreshFlows()
    }

    suspend fun updateDueDate(recordId: Int, newDueDate: Long?) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            if (newDueDate == null) putNull("dueDate") else put("dueDate", newDueDate)
        }
        writableDatabase.update("records", cv, "id = ?", arrayOf(recordId.toString()))
        refreshFlows()
    }

    suspend fun saveRecordAndAdjustBalance(
        record: MoneyRecord,
        sourcePocket: Pocket,
        targetPocket: Pocket? = null
    ) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("title", record.title)
                put("detailNote", record.detailNote)
                put("amountPaise", record.amountPaise)
                put("settledPaise", record.settledPaise)
                put("type", record.type.name)
                put("pocketId", record.pocketId)
                if (record.toPocketId == null) putNull("toPocketId") else put("toPocketId", record.toPocketId)
                if (record.personName == null) putNull("personName") else put("personName", record.personName)
                put("createdAt", record.createdAt)
                if (record.dueDate == null) putNull("dueDate") else put("dueDate", record.dueDate)
                put("isSettled", if (record.isSettled) 1 else 0)
                put("affectsBalance", if (record.affectsBalance) 1 else 0)
            }
            db.insert("records", null, cv)

            if (record.affectsBalance) {
                when (record.type) {
                    RecordType.SPENT, RecordType.TO_TAKE_BACK -> {
                        updatePocketBalanceInternal(db, sourcePocket.id, sourcePocket.balancePaise - record.amountPaise)
                    }
                    RecordType.RECEIVED, RecordType.TO_GIVE_BACK -> {
                        updatePocketBalanceInternal(db, sourcePocket.id, sourcePocket.balancePaise + record.amountPaise)
                    }
                    RecordType.MOVED -> {
                        updatePocketBalanceInternal(db, sourcePocket.id, sourcePocket.balancePaise - record.amountPaise)
                        if (targetPocket != null) {
                            updatePocketBalanceInternal(db, targetPocket.id, targetPocket.balancePaise + record.amountPaise)
                        }
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshFlows()
    }

    suspend fun deleteRecordAndRevertBalance(record: MoneyRecord) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("records", "id = ?", arrayOf(record.id.toString()))

            if (record.affectsBalance) {
                val sourcePocket = _pocketsFlow.value.find { it.id == record.pocketId }
                if (sourcePocket != null) {
                    when (record.type) {
                        RecordType.SPENT -> {
                            updatePocketBalanceInternal(db, sourcePocket.id, sourcePocket.balancePaise + record.amountPaise)
                        }
                        RecordType.RECEIVED -> {
                            updatePocketBalanceInternal(db, sourcePocket.id, sourcePocket.balancePaise - record.amountPaise)
                        }
                        RecordType.TO_TAKE_BACK -> {
                            // Unreturned portion was still out of pocket; settled portion already returned
                            val unreturned = record.amountPaise - record.settledPaise
                            updatePocketBalanceInternal(db, sourcePocket.id, sourcePocket.balancePaise + unreturned)
                        }
                        RecordType.TO_GIVE_BACK -> {
                            // Unpaid portion was still added to pocket
                            val unpaid = record.amountPaise - record.settledPaise
                            updatePocketBalanceInternal(db, sourcePocket.id, sourcePocket.balancePaise - unpaid)
                        }
                        RecordType.MOVED -> {
                            updatePocketBalanceInternal(db, sourcePocket.id, sourcePocket.balancePaise + record.amountPaise)
                            val targetPocket = record.toPocketId?.let { tid -> _pocketsFlow.value.find { it.id == tid } }
                            if (targetPocket != null) {
                                updatePocketBalanceInternal(db, targetPocket.id, targetPocket.balancePaise - record.amountPaise)
                            }
                        }
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshFlows()
    }

    suspend fun settleLoanRecord(
        record: MoneyRecord,
        settleAmountPaise: Long,
        pocket: Pocket
    ) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val newSettled = (record.settledPaise + settleAmountPaise).coerceAtMost(record.amountPaise)
            val fullySettled = newSettled >= record.amountPaise

            val cv = ContentValues().apply {
                put("settledPaise", newSettled)
                put("isSettled", if (fullySettled) 1 else 0)
            }
            db.update("records", cv, "id = ?", arrayOf(record.id.toString()))

            if (record.type == RecordType.TO_TAKE_BACK) {
                updatePocketBalanceInternal(db, pocket.id, pocket.balancePaise + settleAmountPaise)
            } else if (record.type == RecordType.TO_GIVE_BACK) {
                updatePocketBalanceInternal(db, pocket.id, pocket.balancePaise - settleAmountPaise)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshFlows()
    }

    private fun updatePocketBalanceInternal(db: SQLiteDatabase, pocketId: Int, newBalancePaise: Long) {
        val cv = ContentValues().apply { put("balancePaise", newBalancePaise) }
        db.update("pockets", cv, "id = ?", arrayOf(pocketId.toString()))
    }

    companion object {
        @Volatile
        private var INSTANCE: BlueLogDatabase? = null

        fun getDatabase(context: Context): BlueLogDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BlueLogDatabase(context).also { INSTANCE = it }
            }
        }
    }
}