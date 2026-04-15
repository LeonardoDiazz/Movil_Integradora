package com.sgr.app.ui.user

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.sgr.app.R
import com.sgr.app.databinding.ActivityUserBinding
import com.sgr.app.ui.auth.LoginActivity
import com.sgr.app.utils.SessionManager

class UserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        setSupportActionBar(binding.toolbar)

        val toggle = ActionBarDrawerToggle(this, binding.drawerLayout, binding.toolbar, 0, 0)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Header info
        val header = binding.navView.getHeaderView(0)
        val initial = session.userName?.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        header.findViewById<android.widget.TextView>(R.id.tvNavAvatar).text = initial
        header.findViewById<android.widget.TextView>(R.id.tvNavName).text =
            "${session.userName} ${session.userLastName}"
        header.findViewById<android.widget.TextView>(R.id.tvNavEmail).text = session.userEmail

        binding.navView.setNavigationItemSelectedListener { item ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.nav_home -> showFragment(HomeFragment())
                R.id.nav_new_request -> showFragment(NewRequestFragment())
                R.id.nav_my_requests -> showFragment(MyRequestsFragment())
                R.id.nav_profile_user -> showFragment(UserProfileFragment())
            }
            true
        }

        binding.btnLogout.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            logout()
        }

        if (savedInstanceState == null) {
            showFragment(HomeFragment())
            binding.navView.setCheckedItem(R.id.nav_home)
        }
    }

    private fun showFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun navigateTo(fragment: androidx.fragment.app.Fragment, menuItemId: Int) {
        showFragment(fragment)
        binding.navView.setCheckedItem(menuItemId)
    }

    private fun logout() {
        session.clear()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }
}
