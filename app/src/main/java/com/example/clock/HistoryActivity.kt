package com.example.clock

import android.content.Intent
import android.database.DataSetObserver
import android.os.Bundle
import android.widget.TextView
import androidx.core.view.get
import androidx.fragment.app.FragmentActivity
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import com.example.clock.databinding.ActivityHistoryBinding
import com.example.clock.ui.main.SectionsPagerAdapter
import com.example.clock.ui.main.TAB_TITLES

class HistoryActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val resIntent = Intent()
        supportFragmentManager.setFragmentResultListener(
            "history",
            this
        ) { requestKey, result ->
            setResult(RESULT_OK, resIntent.apply {
                result.getString("url")?.let {
                    putExtra("url", it)
                }
            })
            finish()

        }
        supportFragmentManager.setFragmentResultListener(
            "changed",
            this
        ) { requestKey, result ->
            setResult(RESULT_OK, resIntent.apply {
                putExtra("changed", result.getBoolean("changed"))
            })
        }


        val sectionsPagerAdapter =
            SectionsPagerAdapter(this, supportFragmentManager)
        val viewPager: ViewPager = binding.viewPager

        val tabs = binding.tabsBox

        sectionsPagerAdapter.registerDataSetObserver(object : DataSetObserver() {
            override fun onChanged() {
                super.onChanged()
                tabs.removeAllViews()
                for (i in 0 until sectionsPagerAdapter.count) {
                    val view =
                        layoutInflater.inflate(R.layout.simple_tab_item, tabs, false) as TextView
                    view.text = sectionsPagerAdapter.getPageTitle(i)

                    view.isActivated = viewPager.currentItem == i

                    view.setOnClickListener {
                        viewPager.currentItem = i
                    }
                    tabs.addView(view)
                }
            }
        })
        sectionsPagerAdapter.notifyDataSetChanged()
        viewPager.adapter = sectionsPagerAdapter

        viewPager.addOnPageChangeListener(object : OnPageChangeListener {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
            }

            override fun onPageSelected(position: Int) {
                for (i in 0 until tabs.childCount) {
                    val v = tabs[i]
                    if (v.isActivated && i != position) {
                        v.isActivated = false
                    } else if (i == position && !v.isActivated) {
                        v.isActivated = true
                    }
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
            }

        })

        TAB_TITLES.indexOf(intent.extras?.getInt("page")).let{
            if(it != -1 && it != viewPager.currentItem) {
                viewPager.currentItem = it
            }
        }

    }

}