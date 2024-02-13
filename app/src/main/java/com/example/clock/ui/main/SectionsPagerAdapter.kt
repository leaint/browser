package com.example.clock.ui.main

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.example.clock.R

public val TAB_TITLES = arrayOf(
    R.string.tab_text_1,
    R.string.tab_text_2
)

/**
 * A [FragmentPagerAdapter] that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
class SectionsPagerAdapter(
    context: Context,
    fm: FragmentManager,
) :
    FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    private val tabTitles = TAB_TITLES.map {
        context.getString(it)
    }

    override fun getItem(position: Int): Fragment {
        // getItem is called to instantiate the fragment for the given page.
        // Return a PlaceholderFragment.
        return when (position) {
            0 -> {
                BookmarkFragment.newInstance(position + 1)
            }

            else -> HistoryFragment.newInstance(position + 1)
        }

    }

    override fun getPageTitle(position: Int): CharSequence {
        return tabTitles[position]
    }

    override fun getCount(): Int {
        // Show 2 total pages.
        return 2
    }
}