package com.example.clock.ui.main

import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.clock.ui.model.BookMark
import com.example.clock.utils.HistoryItem
import com.example.clock.utils.HistoryTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryViewModel(
    private val db: SQLiteDatabase,
) : ViewModel() {

    private val _index = MutableLiveData<Int>()

    val _keyword = MutableLiveData<String>()

    val history = MediatorLiveData<ArrayList<HistoryItem>>().apply {

        this.addSource(_keyword) {
            viewModelScope.launch(Dispatchers.IO) {
                // 3d
                val lastTime = System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000L
                val historyList = ArrayList<HistoryItem>()
                val selection = if (it.isNotEmpty()) {
                    "${HistoryTable.HistoryEntry.COLUMN_TITLE} LIKE ? OR ${HistoryTable.HistoryEntry.COLUMN_URL} LIKE ?"
                } else {
                    "${HistoryTable.HistoryEntry._ID} > ?"
                }

                val args = if (it.isNotEmpty()) {
                    arrayOf("%${it}%", "%${it}%")
                } else {
                    arrayOf("$lastTime")
                }
                db.query(
                    HistoryTable.HistoryEntry.TABLE_NAME,
                    null,
                    selection,
                    args,
                    null,
                    null,
                    "${HistoryTable.HistoryEntry._ID} DESC"
                ).use {

                    val a = it.getColumnIndexOrThrow(HistoryTable.HistoryEntry._ID)
                    val b = it.getColumnIndexOrThrow(HistoryTable.HistoryEntry.COLUMN_URL)
                    val c = it.getColumnIndexOrThrow(HistoryTable.HistoryEntry.COLUMN_TITLE)

                    while (it.moveToNext()) {
                        historyList.add(
                            HistoryItem(
                                it.getLong(a),
                                it.getString(b),
                                it.getString(c)
                            )
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    value = historyList
                }
//                emit(historyList)

            }
        }

    }

    val text: LiveData<String> = _index.map {
        "Hello world from section: $it"
    }

    fun setIndex(index: Int) {
        _index.value = index
    }

    /**
     * @param offset hour
     */
    fun clearHistory(offset: Int, invert: Boolean = false ) {

        if (offset > 0) {
            val time = System.currentTimeMillis() - 60 * 60 * 1000L * offset
            val invertClause = if (invert)  "<"  else  ">"
            db.transaction {
                delete(
                    HistoryTable.HistoryEntry.TABLE_NAME,
                    "${HistoryTable.HistoryEntry._ID} $invertClause ?",
                    arrayOf(time.toString())
                )
            }

        } else if (offset == -1) {
            db.transaction {
                delete(HistoryTable.HistoryEntry.TABLE_NAME, null, null)
            }
        }
    }

    companion object {
        const val DB = "DB"

        private object DBImpl : CreationExtras.Key<SQLiteDatabase>

        @JvmField
        val DB_KEY: CreationExtras.Key<SQLiteDatabase> = DBImpl

        @Suppress("UNCHECKED_CAST")
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {

                val db = checkNotNull(extras[DB_KEY])

                return HistoryViewModel(db) as T
            }
        }
    }

}

class BookmarkViewModel : ViewModel() {

    private val _index = MutableLiveData<Int>()
    private val _data = MutableLiveData<ArrayList<BookMark>>()

    val text: LiveData<String> = _index.map {
        "Hello world from section: $it"
    }
    val data: LiveData<ArrayList<BookMark>> = _data

    fun setIndex(index: Int) {
        _index.value = index

    }

    fun setData(d: ArrayList<BookMark>) {
        _data.value = d
    }
}
