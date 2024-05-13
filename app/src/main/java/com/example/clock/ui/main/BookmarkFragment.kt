package com.example.clock.ui.main

import android.app.AlertDialog
import android.database.DataSetObserver
import android.os.Bundle
import android.util.JsonWriter
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.clock.R
import com.example.clock.databinding.FragmentHistoryBinding
import com.example.clock.settings.GlobalWebViewSetting
import com.example.clock.settings.arr
import com.example.clock.settings.obj
import com.example.clock.ui.model.BookMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.time.format.DateTimeFormatter

/**
 * A placeholder fragment containing a simple view.
 */
class BookmarkFragment : Fragment() {

    private lateinit var pageViewModel: BookmarkViewModel
    private var _binding: FragmentHistoryBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val timeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("MM-dd")

    private var bookMarks = ArrayList<BookMark>()
    private var bookmarkChanged = false

    private var adapter5: ArrayAdapter<BookMark>? = null

    private val bundle = Bundle()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageViewModel =
            ViewModelProvider(this@BookmarkFragment)[BookmarkViewModel::class.java].apply {
                setIndex(arguments?.getInt(ARG_SECTION_NUMBER) ?: 1)
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        val root = binding.root
        val textView: TextView = binding.sectionLabel

        context?.let {
            val ctx = it

            lifecycleScope.launch {

                lifecycleScope.launch(Dispatchers.IO) {
                    val f = File(
                        ctx.getExternalFilesDir(null),
                        GlobalWebViewSetting.BOOKMARK_FILE
                    )

                    if (f.exists()) {
                        f.inputStream().use { input ->
                            run {
                                val buf = Files.readAllBytes(f.toPath())
                                val bookmarkArr = ArrayList<BookMark>()
                                GlobalWebViewSetting.parseBookMarkFile(buf, bookmarkArr)

                                bookMarks = bookmarkArr
                                bookmarkChanged = false
                            }
                        }
                    }
                }.join()

                pageViewModel.setData(bookMarks)

            }

            with(pageViewModel) {

                data.observe(viewLifecycleOwner) {

                    adapter5 =
                        object :
                            ArrayAdapter<BookMark>(
                                ctx,
                                R.layout.simple_history_item,
                                0,
                                bookMarks
                            ) {
                            override fun getView(
                                position: Int,
                                convertView: View?,
                                parent: ViewGroup
                            ): View {

                                val view = convertView ?: inflater.inflate(
                                    R.layout.simple_history_item,
                                    parent,
                                    false
                                )

                                getItem(position)?.let {
                                    view.findViewById<TextView>(R.id.text1)?.text =
                                        it.title
                                    view.findViewById<TextView>(R.id.text2)?.text =
                                        it.url
                                }

                                return view
                            }
                        }.apply {

                            registerDataSetObserver(object : DataSetObserver() {
                                override fun onChanged() {
                                    bookmarkChanged = true
                                    bundle.putBoolean("changed", bookmarkChanged)
                                    setFragmentResult("changed", bundle)
                                }
                            })
                        }
                    binding.longList.adapter = adapter5

                    text.observe(viewLifecycleOwner) {
                        textView.text = it
                    }
                }

            }

        }


        binding.longList.setOnItemClickListener { parent, view, position, id ->
            val v = pageViewModel.data.value
            if (v != null) {

                setFragmentResult(
                    "history",
                    bundle.apply { putString("url", v[position].url) })
            }
        }

        binding.longList.setOnCreateContextMenuListener { menu, v, menuInfo ->
            run {
                menu.add(1, 0, 0, "Edit")
                menu.add(1, 1, 0, "Delete")
                menu.add(1, 2, 0, "Add to HomePage")
            }
        }
//
//        binding.longList.setOnItemLongClickListener { parent, view, position, id ->
//            run {
//                parent.showContextMenu()
//                true
//            }
//        }

        return root
    }

    override fun onPause() {
        super.onPause()

        if (bookmarkChanged) {

            context?.let {
                File(
                    it.getExternalFilesDir(null),
                    GlobalWebViewSetting.BOOKMARK_FILE
                ).outputStream().use {
                    try {
                        JsonWriter(it.writer()).use {
                            it.obj {
                                arr("bookmarks") {
                                    bookMarks.forEach {
                                        obj {
                                            it.writeJSON(this)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("json", "onPause: BookmarkFragment.kt")
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.groupId == 1) {
            val info = item.menuInfo as AdapterContextMenuInfo
            val v = pageViewModel.data.value
            if (v != null) {
                when (item.itemId) {
                    0 -> {

                        AlertDialog.Builder(context).apply {

                            setTitle("Edit Bookmark")
                            setView(R.layout.edit_bookmark_dialog)
                            setPositiveButton(android.R.string.ok) { dialog, _ ->
                                run {

                                    val alertDialog = dialog as AlertDialog
                                    val a = alertDialog.findViewById<EditText>(R.id.title_edittext)
                                    val b = alertDialog.findViewById<EditText>(R.id.url_edittext)

                                    if (a != null && b != null && a.text.isNotBlank() && b.text.isNotBlank()) {

                                        bookMarks[info.position].title = a.text.toString()
                                        bookMarks[info.position].url = b.text.toString()
                                        bookmarkChanged = true
                                        adapter5?.notifyDataSetChanged()
                                    }
                                }
                            }
                            setNegativeButton(android.R.string.cancel, null)
                        }.create().apply {

                            setOnShowListener {
                                findViewById<EditText>(R.id.title_edittext)?.setText(bookMarks[info.position].title)
                                findViewById<EditText>(R.id.url_edittext)?.setText(bookMarks[info.position].url)
                            }

                        }.show()
                    }

                    1 -> {
                        AlertDialog.Builder(context).apply {
                            setTitle("确定要删除此书签吗？")
                            setIconAttribute( android.R.attr.alertDialogIcon)
                            setPositiveButton(android.R.string.ok) {_,_-> run{
                                bookMarks.removeAt(info.position)
                                adapter5?.notifyDataSetChanged()
                            }}
                            setNegativeButton(android.R.string.cancel, null)
                        }.show()
                    }

                    2 -> {
                        bookMarks[info.position].pinned = true
                        adapter5?.notifyDataSetChanged()
                        context?.let {
                            Toast.makeText(it, "Pinned", Toast.LENGTH_SHORT).show()
                        }

                    }
                }
            }
        }

        return super.onContextItemSelected(item)
    }

    companion object {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private const val ARG_SECTION_NUMBER = "section_number"

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        @JvmStatic
        fun newInstance(sectionNumber: Int): BookmarkFragment {
            return BookmarkFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SECTION_NUMBER, sectionNumber)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}