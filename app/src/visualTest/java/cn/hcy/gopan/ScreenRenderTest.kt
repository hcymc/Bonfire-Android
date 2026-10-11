package cn.hcy.gopan

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test

/** Layoutlib renders the actual production composables with labelled sample content. */
class ScreenRenderTest {
    @get:Rule
    val render =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(locale = "zh-rCN"),
            theme = "android:Theme.Material.Light.NoActionBar",
        )

    @Test
    fun fileScreen() {
        render.snapshot {
            val scope = rememberCoroutineScope()
            val state = remember {
                DriveState(render.context, scope).apply {
                    user =
                        Api.json(
                            "id",
                            1,
                            "username",
                            "hcy",
                            "display_name",
                            "山间来信",
                            "quota_bytes",
                            2147483648L,
                            "used_bytes",
                            754974720L,
                        )
                    folders =
                        listOf(
                            Api.json("id", 1, "name", "旅行与日常", "created_at", "2026-10-05"),
                            Api.json("id", 2, "name", "工作与灵感", "created_at", "2026-10-04"),
                        )
                    files =
                        listOf(
                            Api.json(
                                "id",
                                3,
                                "name",
                                "写给秋天的一封信.txt",
                                "size",
                                4096,
                                "updated_at",
                                "2026-10-05",
                            ),
                            Api.json(
                                "id",
                                4,
                                "name",
                                "山野声音.m4a",
                                "size",
                                18874368,
                                "updated_at",
                                "2026-10-04",
                            ),
                            Api.json(
                                "id",
                                5,
                                "name",
                                "设计灵感集.pdf",
                                "size",
                                8388608,
                                "updated_at",
                                "2026-10-03",
                            ),
                        )
                }
            }
            DriveTheme(state) {
                Scaffold(
                    bottomBar = { DriveNavigation(0) {} },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = {},
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            icon = {
                                Icon(androidx.compose.material.icons.Icons.Outlined.Add, null)
                            },
                            text = { Text("上传文件") },
                        )
                    },
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        FilesPage(state, {}, {}, { _, _ -> }, { _, _ -> }, {}, {})
                    }
                }
            }
        }
    }

    private val registryOwner =
        object : androidx.activity.result.ActivityResultRegistryOwner {
            override val activityResultRegistry =
                object : androidx.activity.result.ActivityResultRegistry() {
                    override fun <I, O> onLaunch(
                        requestCode: Int,
                        contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
                        input: I,
                        options: androidx.core.app.ActivityOptionsCompat?,
                    ) {}
                }
        }

    // Layoutlib's placeholder editor does not implement Android's fluent Editor contract.
    // Keep real preference writes in the state test using a small in-memory fixture.
    private fun fixtureContext(): android.content.Context {
        val values = mutableMapOf<String, Any?>()
        lateinit var editor: android.content.SharedPreferences.Editor
        editor =
            java.lang.reflect.Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(android.content.SharedPreferences.Editor::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "apply" -> null
                    "commit" -> true
                    "clear" -> {
                        values.clear()
                        editor
                    }
                    "remove" -> {
                        values.remove(args[0])
                        editor
                    }
                    else -> {
                        if (method.name.startsWith("put")) values[args[0] as String] = args[1]
                        editor
                    }
                }
            } as android.content.SharedPreferences.Editor
        val preferences =
            java.lang.reflect.Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(android.content.SharedPreferences::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "edit" -> editor
                    "getAll" -> values.toMap()
                    "contains" -> values.containsKey(args[0])
                    "registerOnSharedPreferenceChangeListener",
                    "unregisterOnSharedPreferenceChangeListener" -> null
                    else -> values[args[0]] ?: args[1]
                }
            } as android.content.SharedPreferences
        return object : android.content.ContextWrapper(render.context) {
            override fun getSharedPreferences(name: String?, mode: Int) = preferences
        }
    }

    private fun fixture(tab: Int): DriveState =
        DriveState(fixtureContext(), CoroutineScope(SupervisorJob() + Dispatchers.Main)).apply {
            this.tab = tab
            user =
                Api.json(
                    "id",
                    1,
                    "username",
                    "hcy",
                    "display_name",
                    "山间来信",
                    "bio",
                    "把生活的碎片，妥善珍藏。",
                    "used_bytes",
                    754974720L,
                    "quota_bytes",
                    2147483648L,
                )
            theme = "light"
            accent = 0
        }

    @Composable
    private fun Frame(state: DriveState, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            androidx.activity.compose.LocalActivityResultRegistryOwner provides registryOwner
        ) {
            DriveTheme(state) {
                Scaffold(bottomBar = { if (state.page == "main") DriveNavigation(state.tab) {} }) {
                    padding ->
                    Box(Modifier.padding(padding)) { content() }
                }
            }
        }
    }

    @Test
    fun profileScreen() {
        val state = fixture(4)
        render.snapshot { Frame(state) { ProfilePage(state, {}, {}, {}, {}, {}) } }
        state.scope.cancel()
    }

    @Test
    fun transferScreen() {
        val state =
            fixture(1).apply {
                tasks =
                    listOf(
                        Api.json(
                            "id",
                            1,
                            "name",
                            "旅行日记.mp4",
                            "kind",
                            "upload",
                            "state",
                            "running",
                            "size",
                            134217728L,
                            "done",
                            78643200L,
                        ),
                        Api.json(
                            "id",
                            2,
                            "name",
                            "项目资料.zip",
                            "kind",
                            "download",
                            "state",
                            "paused",
                            "size",
                            67108864L,
                            "done",
                            16777216L,
                        ),
                    )
            }
        render.snapshot { Frame(state) { TransfersPage(state, {}, {}) } }
        state.scope.cancel()
    }

    @Test
    fun shareScreen() {
        val state = fixture(2)
        render.snapshot { Frame(state) { SharesPage(state) {} } }
        state.scope.cancel()
    }

    @Test
    fun settingsUpdatesWithoutRestart() {
        val state = fixture(4).apply { page = "settings" }
        render.snapshot(name = "orange") { Frame(state) { SettingsPage(state) {} } }
        state.updateAccent(1)
        org.junit.Assert.assertEquals(1, state.prefs.getInt("accent", -1))
        render.snapshot(name = "blue") {
            Frame(state) {
                org.junit.Assert.assertEquals(accents[1], MaterialTheme.colorScheme.primary)
                org.junit.Assert.assertEquals(accents[1], MaterialTheme.colorScheme.secondary)
                SettingsPage(state) {}
            }
        }
        state.scope.cancel()
    }

    @Test
    fun welcomeScreen() {
        render.snapshot {
            val scope = rememberCoroutineScope()
            val state = remember { DriveState(render.context, scope) }
            DriveTheme(state) { Welcome(state) }
        }
    }
}
