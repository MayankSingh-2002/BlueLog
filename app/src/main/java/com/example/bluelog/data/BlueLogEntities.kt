package com.example.bluelog.data

enum class PocketType {
    CASH,   // Purse, Hidden Locker, Under the Bed
    ONLINE  // SBI Bank, GPay, PhonePe
}

enum class RecordType {
    SPENT,         // Normal expense (-₹20 samosa)
    RECEIVED,      // Pocket money / Fare money received (+₹100)
    MOVED,         // ATM withdrawal or moving money between pockets
    TO_TAKE_BACK,  // Money given to a friend (Lent)
    TO_GIVE_BACK   // Money borrowed from someone (Borrowed)
}

data class Pocket(
    val id: Int = 0,
    val name: String,
    val type: PocketType,
    val balancePaise: Long = 0L
)

data class MoneyRecord(
    val id: Int = 0,
    val title: String,
    val detailNote: String = "",
    val amountPaise: Long,
    val settledPaise: Long = 0L,
    val type: RecordType,
    val pocketId: Int,
    val toPocketId: Int? = null,
    val personName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val isSettled: Boolean = false,
    val affectsBalance: Boolean = true // false = Past/Opening loan that doesn't alter current pocket balance
)