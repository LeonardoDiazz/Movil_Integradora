package com.sgr.app.ui.admin

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.sgr.app.R
import com.sgr.app.databinding.ActivityAdminBinding
import com.sgr.app.ui.auth.LoginActivity
import com.sgr.app.utils.SessionManager

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        setSupportActionBar(binding.toolbar)

        val toggle = ActionBarDrawerToggle(this, binding.drawerLayout, binding.toolbar, 0, 0)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Set header info
        val header = binding.navView.getHeaderView(0)
        val initial = session.userName?.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        header.findViewById<android.widget.TextView>(R.id.tvNavAvatar).text = initial
        header.findViewById<android.widget.TextView>(R.id.tvNavName).text =
            "${session.userName} ${session.userLastName}"
        header.findViewById<android.widget.TextView>(R.id.tvNavEmail).text = session.userEmail

        binding.navView.setNavigationItemSelectedListener { item ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.nav_users -> showFragment(UsersFragment())
                R.id.nav_spaces -> showFragment(SpacesFragment())
                R.id.nav_equipment -> showFragment(EquipmentFragment())
                R.id.nav_reservations -> showFragment(ReservationsFragment())
                R.id.nav_audit -> showFragment(AuditFragment())
                R.id.nav_profile -> showFragment(ProfileFragment())
            }
            true
        }

        binding.btnLogout.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            logout()
        }

        if (savedInstanceState == null) {
            showFragment(UsersFragment())
            binding.navView.setCheckedItem(R.id.nav_users)
        }
    }

    private fun showFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
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
