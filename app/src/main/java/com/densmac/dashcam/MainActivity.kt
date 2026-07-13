package com.densmac.dashcam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.densmac.dashcam.core.design.theme.DashcamTheme
import com.densmac.dashcam.data.datastore.UserPreferences
import com.densmac.dashcam.data.datastore.UserPreferencesDataSource
import com.densmac.dashcam.ui.navigation.AppNavGraph
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var preferencesDataSource: UserPreferencesDataSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences by preferencesDataSource.preferences.collectAsStateWithLifecycle(UserPreferences())
            DashcamTheme(
                themeMode = preferences.themeMode,
                dynamicColor = preferences.dynamicColorEnabled
            ) {
                AppNavGraph(hapticsEnabled = preferences.hapticsEnabled)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    DashcamTheme {
        AppNavGraph()
    }
}
