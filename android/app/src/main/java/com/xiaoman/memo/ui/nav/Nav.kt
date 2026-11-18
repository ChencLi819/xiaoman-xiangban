package com.xiaoman.memo.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navDeepLink
import com.xiaoman.memo.ui.screens.CycleEditScreen
import com.xiaoman.memo.ui.screens.CycleScreen
import com.xiaoman.memo.ui.screens.DetailScreen
import com.xiaoman.memo.ui.screens.DrawScreen
import com.xiaoman.memo.ui.screens.EditorScreen
import com.xiaoman.memo.ui.screens.FlipBrowseScreen
import com.xiaoman.memo.ui.screens.HomeScreen
import com.xiaoman.memo.ui.screens.InteractScreen
import com.xiaoman.memo.ui.screens.LettersScreen
import com.xiaoman.memo.ui.screens.LibraryScreen
import com.xiaoman.memo.ui.screens.LetterEditScreen
import com.xiaoman.memo.ui.screens.ListsScreen
import com.xiaoman.memo.ui.screens.MailEditScreen
import com.xiaoman.memo.ui.screens.MailReadScreen
import com.xiaoman.memo.ui.screens.MailScreen
import com.xiaoman.memo.ui.screens.MemosScreen
import com.xiaoman.memo.ui.screens.MoodHistoryScreen
import com.xiaoman.memo.ui.screens.PhotosScreen
import com.xiaoman.memo.ui.screens.PlansScreen
import com.xiaoman.memo.ui.screens.PoolBrowseScreen
import com.xiaoman.memo.ui.screens.QuarrelEditScreen
import com.xiaoman.memo.ui.screens.QuarrelsScreen
import com.xiaoman.memo.ui.screens.RuleEditScreen
import com.xiaoman.memo.ui.screens.RulesScreen
import com.xiaoman.memo.ui.screens.UsScreen
import com.xiaoman.memo.ui.screens.UsageScreen
import com.xiaoman.memo.ui.theme.Xm
import kotlinx.coroutines.launch

object R {
    /* tab 由深链（桌面小工具）定位：0 此刻 / 1 说好的 / 2 点滴 / 3 往后 / 4 我们俩 */
    const val TABS = "tabs?tab={tab}"
    /* draft 由小工具「快速记一笔」带入的草稿文本（可选） */
    const val EDITOR = "editor?memoId={memoId}&draft={draft}"
    const val DETAIL = "detail/{memoId}"
    const val QUARRELS = "quarrels"
    const val QUARREL_EDIT = "quarrelEdit?quarrelId={quarrelId}&heat={heat}"
    const val DRAW = "draw?quarrelId={quarrelId}&heat={heat}"
    const val POOL_LIB = "poolLib"
    const val INTERACT = "interact"
    const val FLIP = "flip"
    const val LIBRARY = "library"
    const val MOOD = "mood"
    const val CYCLE = "cycle"
    const val CYCLE_EDIT = "cycleEdit?cycleId={cycleId}&daily={daily}"
    const val LETTERS = "letters"
    const val LETTER_EDIT = "letterEdit?letterId={letterId}"
    /* 云笺：只增不删，故没有删除路由 */
    const val MAILS = "mails"
    const val MAIL_READ = "mailRead?mailId={mailId}"
    const val MAIL_EDIT = "mailEdit"
    const val RULES = "rules"
    const val RULE_EDIT = "ruleEdit?ruleId={ruleId}"
    const val PHOTOS = "photos"
    const val USAGE = "usage"
}

private data class TabItem(val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem("此刻", Icons.Outlined.Edit),
    TabItem("说好的", Icons.Outlined.Checklist),
    TabItem("点滴", Icons.Outlined.Notes),
    TabItem("往后", Icons.Outlined.CalendarMonth),
    TabItem("我们俩", Icons.Outlined.PeopleAlt),
)

@Composable
fun XiaomanNavHost(nav: NavHostController, vm: com.xiaoman.memo.data.AppViewModel) {
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val c = Xm
    val scope = rememberCoroutineScope()

    /* 5 个主页面用 HorizontalPager 承载：左右滑动翻页，与底部 Tab 双向联动 */
    val pagerState = rememberPagerState(initialPage = 0) { TABS.size }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars,
        bottomBar = {
            if (route == R.TABS) {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    )
                    NavigationBar(containerColor = c.navbg, tonalElevation = 0.dp) {
                        TABS.forEachIndexed { i, t ->
                            val on = pagerState.currentPage == i
                            NavigationBarItem(
                                selected = on,
                                onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                                icon = { Icon(t.icon, null) },
                                label = { Text(t.label, fontSize = 12.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = c.accentDeep,
                                    selectedTextColor = c.accentDeep,
                                    indicatorColor = c.accentSoft,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = R.TABS,
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding(),
        ) {
            composable(
                R.TABS,
                deepLinks = listOf(navDeepLink { uriPattern = "xiaoman://tabs?tab={tab}" }),
            ) { e ->
                /* 深链带 tab 时直接翻到对应主页（小工具的「去往后」等入口用） */
                val tab = e.arguments?.getString("tab")?.toIntOrNull() ?: 0
                LaunchedEffect(tab) {
                    if (tab in 1..(TABS.size - 1)) pagerState.scrollToPage(tab)
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 2,
                ) { page ->
                    when (page) {
                        0 -> HomeScreen(nav, vm) { target -> scope.launch { pagerState.animateScrollToPage(target) } }
                        1 -> ListsScreen(nav, vm)
                        2 -> MemosScreen(nav, vm)
                        3 -> PlansScreen(nav, vm)
                        else -> UsScreen(nav, vm)
                    }
                }
            }

            composable(
                R.EDITOR,
                deepLinks = listOf(
                    navDeepLink { uriPattern = "xiaoman://editor" },
                    navDeepLink { uriPattern = "xiaoman://editor?draft={draft}" },
                ),
            ) { e ->
                EditorScreen(
                    nav, vm,
                    memoId = e.arguments?.getString("memoId")?.toLongOrNull() ?: 0L,
                    draft = e.arguments?.getString("draft").orEmpty(),
                )
            }
            composable(R.DETAIL) { e -> DetailScreen(nav, vm, e.arguments?.getString("memoId")?.toLongOrNull() ?: 0L) }
            composable(R.QUARRELS) { QuarrelsScreen(nav, vm) }
            composable(R.QUARREL_EDIT) { e ->
                QuarrelEditScreen(nav, vm, e.arguments?.getString("quarrelId")?.toLongOrNull() ?: 0L, e.arguments?.getString("heat")?.toIntOrNull() ?: 3)
            }
            composable(
                R.DRAW,
                deepLinks = listOf(navDeepLink { uriPattern = "xiaoman://draw" }),
            ) { e ->
                DrawScreen(nav, vm, e.arguments?.getString("quarrelId")?.toLongOrNull(), e.arguments?.getString("heat")?.toIntOrNull() ?: 2)
            }
            composable(R.INTERACT) { InteractScreen(nav, vm) }
            composable(R.FLIP) { FlipBrowseScreen(nav, vm) }
            composable(
                R.POOL_LIB,
                deepLinks = listOf(navDeepLink { uriPattern = "xiaoman://pool" }),
            ) { PoolBrowseScreen(nav, vm) }
            composable(
                R.LIBRARY,
                deepLinks = listOf(navDeepLink { uriPattern = "xiaoman://library" }),
            ) { LibraryScreen(nav, vm) }
            composable(R.MOOD) { MoodHistoryScreen(nav, vm) }
            composable(R.CYCLE) { CycleScreen(nav, vm) }
            composable(R.CYCLE_EDIT) { e ->
                CycleEditScreen(
                    nav, vm,
                    cycleId = e.arguments?.getString("cycleId")?.toLongOrNull() ?: 0L,
                    dailyArg = e.arguments?.getString("daily") == "1",
                )
            }
            composable(R.MAILS) { MailScreen(nav, vm) }
            composable(R.MAIL_READ) { e ->
                MailReadScreen(nav, vm, e.arguments?.getString("mailId")?.toLongOrNull() ?: 0L)
            }
            composable(R.MAIL_EDIT) { MailEditScreen(nav, vm) }
            composable(R.LETTERS) { LettersScreen(nav, vm) }
            composable(
                R.LETTER_EDIT,
                deepLinks = listOf(navDeepLink { uriPattern = "xiaoman://letter" }),
            ) { e ->
                LetterEditScreen(nav, vm, e.arguments?.getString("letterId")?.toLongOrNull() ?: 0L)
            }
            composable(R.RULES) { RulesScreen(nav, vm) }
            composable(R.RULE_EDIT) { e ->
                RuleEditScreen(nav, vm, e.arguments?.getString("ruleId")?.toLongOrNull() ?: 0L)
            }
            composable(R.PHOTOS) { PhotosScreen(nav, vm) }
            composable(R.USAGE) { UsageScreen(nav) }
        }
    }
}
