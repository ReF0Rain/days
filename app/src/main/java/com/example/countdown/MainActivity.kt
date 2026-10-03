package com.example.countdown

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.countdown.data.CountdownRepository
import com.example.countdown.notification.CountdownNotifications
import com.example.countdown.notification.DailyUpdateScheduler
import com.example.countdown.ui.CountdownListViewModel
import com.example.countdown.ui.CountdownViewModelFactory
import com.example.countdown.ui.screens.AddEditScreen
import com.example.countdown.ui.screens.EventListScreen
import com.example.countdown.ui.theme.CountdownTheme

class MainActivity : ComponentActivity() {

    private val repository by lazy { CountdownRepository.getInstance(applicationContext) }

    private val listViewModel: CountdownListViewModel by viewModels {
        CountdownViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CountdownTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    // Android 13+ 通知权限：进入应用就询问一次
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        if (granted) {
                            // 授权成功后立刻注册任务并补发一次通知
                            DailyUpdateScheduler.schedule(applicationContext)
                            DailyUpdateScheduler.runNow(applicationContext)
                        }
                    }

                    LaunchedEffect(Unit) {
                        if (CountdownNotifications.needsPermissionRequest(applicationContext)) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            // 已有权限（或低版本系统）：确保任务在跑
                            DailyUpdateScheduler.schedule(applicationContext)
                            DailyUpdateScheduler.runNow(applicationContext)
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Routes.LIST
                    ) {
                        composable(Routes.LIST) {
                            val state = listViewModel.uiState.collectAsStateWithLifecycle()

                            EventListScreen(
                                state = state.value,
                                onAddClick = { navController.navigate(Routes.edit(0L)) },
                                onEditClick = { event -> navController.navigate(Routes.edit(event.id)) },
                                onDeleteClick = { event -> listViewModel.delete(event) },
                                onToggleSort = {
                                    listViewModel.toggleSortOrder()
                                    DailyUpdateScheduler.runNow(applicationContext)
                                },
                                onRefreshClick = {
                                    DailyUpdateScheduler.runNow(applicationContext)
                                }
                            )
                        }

                        composable(
                            route = Routes.EDIT,
                            arguments = listOf(
                                navArgument(Routes.ARG_ID) {
                                    type = NavType.LongType
                                    defaultValue = 0L
                                }
                            )
                        ) { backStackEntry ->
                            val id = backStackEntry.arguments?.getLong(Routes.ARG_ID) ?: 0L
                            AddEditScreen(
                                eventId = id,
                                factory = CountdownViewModelFactory(repository),
                                onFinished = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 路由集中管理，避免字符串散落各处 */
object Routes {
    const val ARG_ID = "eventId"
    const val LIST = "list"
    const val EDIT = "edit/{$ARG_ID}"

    fun edit(id: Long): String = "edit/$id"
}
