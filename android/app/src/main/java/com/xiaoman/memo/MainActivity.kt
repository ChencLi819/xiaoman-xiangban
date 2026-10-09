package com.xiaoman.memo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.xiaoman.memo.data.AppViewModel
import com.xiaoman.memo.ui.nav.R
import com.xiaoman.memo.ui.nav.XiaomanNavHost
import com.xiaoman.memo.ui.theme.ThemeMode
import com.xiaoman.memo.ui.theme.XiaomanTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var nav: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askNotificationPermission()
        setContent {
            val ui by vm.ui.collectAsState()
            val mode = when (ui.profile.theme) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            XiaomanTheme(mode) {
                val n = rememberNavController()
                nav = n
                XiaomanNavHost(n, vm)
            }
        }
    }

    /* 快捷方式等新 intent：交给 Navigation 解析深链 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        nav?.handleDeepLink(intent)
    }

    private fun askNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }
}
