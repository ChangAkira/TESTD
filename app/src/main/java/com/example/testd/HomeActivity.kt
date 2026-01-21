package com.example.testd

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.testd.ui.Column1Fragment
import com.example.testd.ui.Column2Fragment
import com.example.testd.ui.ProfileFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, Column1Fragment())
                .commit()
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_column1 -> Column1Fragment()
                R.id.nav_column2 -> Column2Fragment()
                R.id.nav_profile -> ProfileFragment()
                else -> Column1Fragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
            true
        }
        bottomNav.selectedItemId = R.id.nav_column1
    }
}
