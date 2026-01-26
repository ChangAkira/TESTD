package com.example.testd.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.testd.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2

class Column2Fragment : Fragment() {
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_column2, container, false)
        tabLayout = view.findViewById(R.id.myTabs)
        viewPager = view.findViewById(R.id.myViewPager)

        val fragments = listOf(
            MyPublishedFragment(),
            MyAcceptedFragment()
        )
        val titles = listOf(
            getString(R.string.my_published),
            getString(R.string.my_accepted)
        )
        viewPager.adapter = MyTasksPagerAdapter(this, fragments)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = titles[position]
        }.attach()
        return view
    }
}
