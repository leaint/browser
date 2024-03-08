package com.example.clock.utils

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Parcel
import android.os.Parcelable
import android.provider.BaseColumns
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.clock.ui.model.SuggestItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HistoryItemRef {

    var value = HistoryItem(0)
    fun reset() {
        value = HistoryItem(0)
    }
}

object HistoryList {
    val historyList = ArrayList<HistoryItem>()
}

class HistoryManager(lifecycleOwner: LifecycleOwner, context: Context) :
    DefaultLifecycleObserver {
    private var historyList = HistoryList.historyList

    private var curSavedHistory = 0

    private val dbHelper = HistoryDbHelper(context)
    private lateinit var historyDb: SQLiteDatabase

    init {
        lifecycleOwner.lifecycle.addObserver(this)
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            historyDb = dbHelper.writableDatabase

        }
    }

    fun addHistoryItem(item: HistoryItem) {
        if (historyList.lastOrNull()?.url != item.url) {
            if (item.title.length > 100) {
                item.title = item.title.substring(0, 100)
            }
            historyList.add(item)
        }
    }

    fun clearAllHistory() {
        historyDb.delete(
            HistoryTable.HistoryEntry.TABLE_NAME, null, null
        )
        historyList.clear()
    }

    fun getTopSite(s: String): ArrayList<SuggestItem> {
        val arr = ArrayList<SuggestItem>()
        if (s.isEmpty()) return arr

        val selection =
            "${HistoryTable.HistoryEntry.COLUMN_TITLE} LIKE ? OR ${HistoryTable.HistoryEntry.COLUMN_URL} LIKE ?"

        val args = arrayOf("%${s}%", "%${s}%")

        historyDb.query(
            HistoryTable.HistoryEntry.VIEW_TOP_NAME,
            null, selection, args, null, null, HistoryTable.HistoryEntry.COLUMN_C, "10"
        ).use {

            val a = it.getColumnIndexOrThrow(HistoryTable.HistoryEntry.COLUMN_URL)
            val b = it.getColumnIndexOrThrow(HistoryTable.HistoryEntry.COLUMN_TITLE)
            val c = it.getColumnIndexOrThrow(HistoryTable.HistoryEntry.COLUMN_C)

            while (it.moveToNext()) {
                arr.add(SuggestItem(it.getInt(c), it.getString(a), it.getString(b)))
            }

        }
        return arr
    }

    override fun onDestroy(owner: LifecycleOwner) {
        dbHelper.close()
    }

    override fun onPause(owner: LifecycleOwner) {
        owner.lifecycleScope.launch(Dispatchers.IO) {
            if (curSavedHistory < historyList.size) {

                historyDb.beginTransaction()

                try {
                    historyDb.compileStatement(
                        "INSERT OR IGNORE INTO ${HistoryTable.HistoryEntry.TABLE_NAME} (" + "${HistoryTable.HistoryEntry._ID}," + "${HistoryTable.HistoryEntry.COLUMN_TITLE}," + "${HistoryTable.HistoryEntry.COLUMN_URL}) VALUES (" + "?,?,?)"
                    ).use {
                        for (h in historyList) {
                            it.apply {
                                bindLong(1, h.time)
                                bindString(2, h.title)
                                bindString(3, h.url)

                                executeInsert()
                            }
                        }
                    }
                    historyList.clear()
                    curSavedHistory = historyList.size
                    historyDb.setTransactionSuccessful()

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    historyDb.endTransaction()
                }
            }
        }

    }
}

data class HistoryItem(var time: Long, var url: String = "", var title: String = "") : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString() ?: "",
        parcel.readString() ?: "",
    )

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(time)
        dest.writeString(url)
        dest.writeString(title)
    }

    override fun describeContents() = 0

    companion object CREATOR : Parcelable.Creator<HistoryItem> {
        override fun createFromParcel(parcel: Parcel): HistoryItem {
            return HistoryItem(parcel)
        }

        override fun newArray(size: Int): Array<HistoryItem?> {
            return arrayOfNulls(size)
        }
    }
}

class HistoryDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(HistoryTable.SQL_CREATE_TABLE)
        db?.execSQL(HistoryTable.SQL_CREATE_TOP_VIEW)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL(HistoryTable.SQL_DELETE_VIEW)
        db?.execSQL(HistoryTable.SQL_DELETE_TABLE)
        onCreate(db)
    }

    companion object {
        const val DATABASE_NAME = "history.db"
        const val DATABASE_VERSION = 3
    }

}

object HistoryTable {

    const val SQL_CREATE_TABLE =
        "CREATE TABLE ${HistoryEntry.TABLE_NAME} (" + "${HistoryEntry._ID} INTEGER PRIMARY KEY," + "${HistoryEntry.COLUMN_TITLE} TEXT," + "${HistoryEntry.COLUMN_URL} TEXT )"

    const val SQL_CREATE_TOP_VIEW =
        "create view ${HistoryEntry.VIEW_TOP_NAME} as select ${HistoryEntry.COLUMN_URL}, ${HistoryEntry.COLUMN_TITLE},count(${HistoryEntry.COLUMN_URL}) as ${HistoryEntry.COLUMN_C} from ${HistoryEntry.TABLE_NAME}  group by ${HistoryEntry.COLUMN_URL} having ${HistoryEntry.COLUMN_C} > ${HistoryEntry.LEN} order by ${HistoryEntry.COLUMN_C} DESC"

    const val SQL_DELETE_TABLE = "DROP TABLE IF EXISTS ${HistoryEntry.TABLE_NAME}"

    const val SQL_DELETE_VIEW = "DROP VIEW  IF EXISTS ${HistoryEntry.VIEW_TOP_NAME}"

    object HistoryEntry : BaseColumns {
        // @Column(Cursor.FIELD_TYPE_INTEGER)
        const val LEN = 2
        const val _ID = "time"
        const val TABLE_NAME = "history"
        const val VIEW_TOP_NAME = "topsite"
        const val COLUMN_C = "c"
        const val COLUMN_TITLE = "title"
        const val COLUMN_URL = "url"
    }

}