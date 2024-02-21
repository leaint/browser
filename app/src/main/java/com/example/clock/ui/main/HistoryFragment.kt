package com.example.clock.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.children
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.MutableCreationExtras
import com.example.clock.R
import com.example.clock.databinding.FragmentHistoryBinding
import com.example.clock.utils.HistoryDbHelper
import com.example.clock.utils.HistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A placeholder fragment containing a simple view.
 */
class HistoryFragment : Fragment() {

    private var pageViewModel: HistoryViewModel? = null
    private var _binding: FragmentHistoryBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val timeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    //    private val dateFormatter = DateTimeFormatter.ofPattern("MM-dd")
    private var dbHelper: HistoryDbHelper? = null
    private var db: SQLiteDatabase? = null

    private fun updateTime() {
        pageViewModel?.history?.value?.getOrNull(binding.longList.firstVisiblePosition)
            ?.let {

                val offset = (System.currentTimeMillis() - it.time) / 1000

                val s = if (offset < 60) {
                    "刚刚"
                } else if (offset < 3600) {
                    "${offset / 60} 分钟前"
                } else if (offset < 86400) {
                    "${offset / 3600} 小时前"
                } else {
                    "${offset / 86400} 天前 " +
                            timeFormatter.format(
                                LocalDateTime.ofEpochSecond(
                                    it.time / 1000,
                                    0,
                                    ZoneOffset.of("+8")
                                )
                            )
                }


                binding.sectionLabel.text = s
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        val root = binding.root

        if (dbHelper == null) {
            dbHelper = HistoryDbHelper(requireContext())
        }

        val scrollChannel = Channel<Unit>(Channel.RENDEZVOUS, BufferOverflow.DROP_OLDEST)
        val adapter5 =
            object :
                ArrayAdapter<HistoryItem>(
                    requireContext(),
                    R.layout.simple_history_item,
                    0,
                    ArrayList<HistoryItem>()
                ) {
                override fun getItemId(position: Int): Long {
                    return getItem(position)!!.time
                }

                override fun hasStableIds(): Boolean {
                    return true
                }

                override fun getView(
                    position: Int,
                    convertView: View?,
                    parent: ViewGroup
                ): View {

                    val view = if (convertView != null) {

                        if (convertView.layoutParams.height == LayoutParams.WRAP_CONTENT) {
                            if (convertView is LinearLayout) {
                                convertView.layoutParams.height = convertView.height
                                convertView.children.forEach {
                                    it.layoutParams.height = it.height
                                }
                            }
                        }

                        convertView
                    } else {
                        inflater.inflate(
                            R.layout.simple_history_item,
                            parent,
                            false
                        )
                    }

                    getItem(position)?.let {
                        view.findViewById<TextView>(R.id.text1)?.text = it.title
                        view.findViewById<TextView>(R.id.text2)?.text = it.url
//                        view.findViewById<TextView>(R.id.text2)?.setText(SpannableStringBuilder(it.url), TextView.BufferType.SPANNABLE)


                    }

                    return view

                }
            }

        binding.longList.adapter = adapter5
        lifecycleScope.launch {

            lifecycleScope.launch(Dispatchers.IO) {
                if (db == null)
                    db = dbHelper!!.writableDatabase
            }.join()


            lifecycleScope.launch {
                while (isActive) {
                    scrollChannel.receive()

                    updateTime()
                    delay(500)
                }
            }

            if (pageViewModel != null) return@launch
            pageViewModel =
                ViewModelProvider(
                    this@HistoryFragment.viewModelStore,
                    HistoryViewModel.Factory,
                    MutableCreationExtras().apply {
                        set(HistoryViewModel.DB_KEY, db!!)
                    })[HistoryViewModel::class.java].apply {
                    setIndex(arguments?.getInt(ARG_SECTION_NUMBER) ?: 1)
                }.apply {

                    _keyword.value = ""
                    history.observe(viewLifecycleOwner) {
//                        if (it.size > 0) {
//
//
//
//                        }

                        adapter5.clear()
                        adapter5.addAll(it)
                    }


                }

        }

        binding.searchText.addTextChangedListener {
            pageViewModel?._keyword?.value = it.toString()
        }

        binding.longList.setOnScrollListener(object :
            AbsListView.OnScrollListener {
            override fun onScrollStateChanged(
                view: AbsListView?,
                scrollState: Int
            ) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    updateTime()
                }
            }

            override fun onScroll(
                view: AbsListView?,
                firstVisibleItem: Int,
                visibleItemCount: Int,
                totalItemCount: Int
            ) {
                scrollChannel.trySend(Unit)
            }

        })

        binding.longList.setOnItemClickListener { parent, view, position, id ->
            val v = pageViewModel?.history?.value
            if (v != null) {

                setFragmentResult(
                    "history",
                    Bundle().apply { putString("url", v[position].url) })
            }
        }

        binding.longList.setOnCreateContextMenuListener { menu, v, menuInfo ->
            run {
                val info = menuInfo as AdapterView.AdapterContextMenuInfo
                val v = pageViewModel?.history?.value
                if (v != null) {
                    val s = timeFormatter.format(
                        LocalDateTime.ofEpochSecond(
                            v[info.position].time / 1000,
                            0,
                            ZoneOffset.of("+8")
                        )
                    )
                    menu.setHeaderTitle(s)
                }

                menu.add(0, 0, 0, R.string.copy_link)
            }
        }

        binding.longList.isFastScrollEnabled

        return root
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.groupId == 0) {
            val info = item.menuInfo as AdapterView.AdapterContextMenuInfo
            val v = pageViewModel?.history?.value
            if (v != null) {
                when (item.itemId) {
                    0 -> {
                        val clipboard =
                            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                "WebView",
                                v[info.position].url
                            )
                        )
                        Toast.makeText(requireContext(), "Copied", Toast.LENGTH_SHORT).show()
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
        fun newInstance(sectionNumber: Int): HistoryFragment {
            return HistoryFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SECTION_NUMBER, sectionNumber)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        dbHelper?.close()
        db = null
        dbHelper = null
    }
}